package com.wifi.monitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight UDP DNS Server (Port 53) that intercepts DNS queries from
 * devices connected to the laptop's Mobile Hotspot.
 * 
 * - If device is in Blocked List: Responds with 0.0.0.0 (Sinkhole) & logs DEVICE BLOCKED event.
 * - If domain is in Blocked List: Responds with 0.0.0.0 (Sinkhole) & logs real-time event.
 * - If domain is Allowed: Relays query to Upstream DNS (1.1.1.1, 8.8.8.8) and returns real answer.
 */
public class DnsFilterServer implements Runnable {

    private static final int DNS_PORT = 53;
    private static final String[] UPSTREAM_SERVERS = {"1.1.1.1", "8.8.8.8", "9.9.9.9", "1.0.0.1"};
    private static final int UPSTREAM_PORT = 53;
    private static final int BUFFER_SIZE = 4096;

    private final RestrictionDAO restrictionDAO = new RestrictionDAO();
    private final BlockedLogDAO blockedLogDAO = new BlockedLogDAO();
    private final DeviceDAO deviceDAO = new DeviceDAO();

    private DatagramSocket socket;
    private volatile boolean running = false;
    private int boundPort = DNS_PORT;
    private String statusMessage = "Initializing";
    private final ExecutorService workerPool = Executors.newCachedThreadPool();

    public void start() {
        if (running) return;
        Thread thread = new Thread(this, "DnsFilterServer-Worker");
        thread.setDaemon(false); // Non-daemon so DNS service stays alive independently
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        workerPool.shutdownNow();
    }

    @Override
    public void run() {
        boolean bound = false;
        SocketException lastException = null;

        // Attempt to bind standard UDP port 53 on all interfaces (0.0.0.0) with retries
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(DNS_PORT));
                boundPort = DNS_PORT;
                running = true;
                bound = true;
                statusMessage = "Active on UDP Port " + DNS_PORT;
                System.out.println("🛡️ DNS Filter Server successfully bound and running on UDP port " + DNS_PORT);
                break;
            } catch (SocketException e) {
                lastException = e;
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (attempt < 3) {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                }
            }
        }

        if (!bound) {
            running = false;
            boundPort = 0;
            String errMsg = lastException != null ? lastException.getMessage() : "Port occupied";
            statusMessage = "Failed: UDP Port 53 unavailable (" + errMsg + ")";
            System.err.println("❌ [CRITICAL] DnsFilterServer could NOT bind to UDP Port 53: " + errMsg);
            System.err.println("👉 Cause: Port 53 is already held (e.g. by AdGuard Home, a previous Java instance, or another DNS resolver).");
            System.err.println("👉 Resolution: Stop conflicting DNS software or kill duplicate Java instances before starting.");
            return;
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                final int length = packet.getLength();
                final byte[] queryData = new byte[length];
                System.arraycopy(packet.getData(), packet.getOffset(), queryData, 0, length);
                final InetAddress clientAddress = packet.getAddress();
                final int clientPort = packet.getPort();

                System.out.println("📥 [DNS RECV] Packet received: " + length + " bytes from " + clientAddress.getHostAddress() + ":" + clientPort);

                // Handle DNS request asynchronously in thread pool
                workerPool.submit(() -> handleDnsQuery(queryData, clientAddress, clientPort));

            } catch (IOException e) {
                if (!running) break;
                System.err.println("❌ [DNS SOCKET ERROR] Exception in receive loop: " + e.getMessage());
            }
        }
    }

    private void handleDnsQuery(byte[] queryData, InetAddress clientAddress, int clientPort) {
        if (queryData.length < 12) {
            System.err.println("⚠️ [DNS PARSE FAIL] Dropping malformed packet (length < 12 bytes) from " + clientAddress.getHostAddress());
            return;
        }

        try {
            String clientIp = clientAddress.getHostAddress();
            deviceDAO.registerDiscoveredClient(clientIp);
            String deviceName = resolveDeviceName(clientIp);

            ParsedDnsQuery parsed = parseDomainName(queryData);
            String domain = parsed.domain;
            System.out.println("🔍 [DNS PARSE] " + clientIp + " -> Domain: '" + domain + "' | QType: " + parsed.qType + " (" + getQTypeName(parsed.qType) + ")");

            // 1. DEVICE BLOCKING CHECK
            // If the entire client device is blocked by admin, sinkhole ALL DNS requests immediately!
            if (deviceDAO.isIpBlocked(clientIp)) {
                System.out.println("🚫 [DNS DECISION] BLOCKED (Device Policy): Client IP " + clientIp + " is blocked. Returning sinkhole.");
                byte[] blockedResponse = buildBlockedResponse(queryData, parsed.questionEndOffset, parsed.qType);
                sendResponse(blockedResponse, clientAddress, clientPort, domain, "DEVICE BLOCKED");

                String target = (domain != null && !domain.isEmpty()) ? domain : "All Network Access";
                blockedLogDAO.recordBlockedRequest(target, clientIp, deviceName, "DEVICE BLOCKED");
                return;
            }

            // 2. LOCAL REVERSE PTR LOOKUP (Fast response for nslookup & OS resolvers)
            if (parsed.qType == 12 && domain != null && (domain.contains("1.0.0.127") || domain.contains("127.in-addr.arpa") || domain.contains("137.168.192"))) {
                System.out.println("⚡ [DNS DECISION] LOCAL PTR: Reverse DNS query for '" + domain + "' -> Returning local hostname.");
                byte[] ptrResponse = buildPtrResponse(queryData, parsed.questionEndOffset, "wifi-usage-monitor.local");
                sendResponse(ptrResponse, clientAddress, clientPort, domain, "LOCAL PTR");
                return;
            }

            // 3. WEBSITE DOMAIN BLOCKING CHECK
            if (domain != null && !domain.isEmpty()) {
                if (restrictionDAO.isDomainBlocked(domain)) {
                    System.out.println("🚫 [DNS DECISION] BLOCKED (Domain Rule): '" + domain + "' matches restriction policy. Returning sinkhole 0.0.0.0 / ::");
                    byte[] blockedResponse = buildBlockedResponse(queryData, parsed.questionEndOffset, parsed.qType);
                    sendResponse(blockedResponse, clientAddress, clientPort, domain, "BLOCKED");

                    // Record real-time event
                    blockedLogDAO.recordBlockedRequest(domain, clientIp, deviceName, "BLOCKED");
                    return;
                }
            }

            // 4. ALLOWED -> Forward to upstream DNS (1.1.1.1 with fallback)
            System.out.println("✅ [DNS DECISION] ALLOWED: '" + domain + "' is allowed. Forwarding to upstream DNS...");
            byte[] upstreamResponse = forwardToUpstreamDns(queryData);
            if (upstreamResponse != null && upstreamResponse.length > 0) {
                sendResponse(upstreamResponse, clientAddress, clientPort, domain, "ALLOWED");
            } else {
                System.out.println("⚠️ [DNS DECISION] Upstream DNS failed/timed out for '" + domain + "'. Sending SERVFAIL response.");
                byte[] servFail = buildServFailResponse(queryData);
                sendResponse(servFail, clientAddress, clientPort, domain, "SERVFAIL");
            }

        } catch (Exception e) {
            System.err.println("❌ [DNS ERROR] Error processing DNS query from " + clientAddress.getHostAddress() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendResponse(byte[] responseData, InetAddress clientAddress, int clientPort, String domain, String status) {
        if (responseData == null || responseData.length == 0) {
            System.err.println("❌ [DNS SEND FAIL] Empty response data for " + clientAddress.getHostAddress() + ":" + clientPort);
            return;
        }
        if (socket == null || socket.isClosed()) {
            System.err.println("❌ [DNS SEND FAIL] Cannot send: UDP socket is closed or null.");
            return;
        }
        try {
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
            socket.send(responsePacket);
            System.out.println("📤 [DNS SENT] Transmitted " + responseData.length + " bytes to " + clientAddress.getHostAddress() + ":" + clientPort + " [" + status + ": " + domain + "]");
        } catch (IOException e) {
            System.err.println("❌ [DNS SEND FAIL] IOException sending " + responseData.length + " bytes to " + clientAddress.getHostAddress() + ":" + clientPort + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ [DNS SEND FAIL] Unexpected error sending to " + clientAddress.getHostAddress() + ":" + clientPort + ": " + e.getMessage());
        }
    }

    private static String getQTypeName(int qType) {
        switch (qType) {
            case 1:   return "A (IPv4)";
            case 28:  return "AAAA (IPv6)";
            case 65:  return "HTTPS";
            case 64:  return "SVCB";
            case 12:  return "PTR (Reverse)";
            case 15:  return "MX (Mail)";
            case 16:  return "TXT";
            case 5:   return "CNAME";
            case 255: return "ANY";
            default:  return "Type " + qType;
        }
    }

    /**
     * Extracts the QNAME domain string and QTYPE from the DNS Question section.
     */
    private static class ParsedDnsQuery {
        String domain;
        int qType = 1; // 1 = A, 28 = AAAA, 65 = HTTPS
        int questionEndOffset;
    }

    private ParsedDnsQuery parseDomainName(byte[] data) {
        ParsedDnsQuery res = new ParsedDnsQuery();
        StringBuilder sb = new StringBuilder();
        int offset = 12; // Skip 12-byte header

        while (offset < data.length) {
            int len = data[offset] & 0xFF;
            if (len == 0) {
                offset++;
                break;
            }
            if ((len & 0xC0) == 0xC0) { // Pointer compression
                offset += 2;
                break;
            }
            offset++;
            for (int i = 0; i < len && offset < data.length; i++) {
                sb.append((char) (data[offset] & 0xFF));
                offset++;
            }
            sb.append('.');
        }

        // Strip trailing dot
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
            sb.deleteCharAt(sb.length() - 1);
        }

        res.domain = sb.toString().toLowerCase().trim();

        // Extract QTYPE (2 bytes after QNAME)
        int qType = 1;
        if (offset + 2 <= data.length) {
            qType = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        }
        res.qType = qType;
        res.questionEndOffset = Math.min(offset + 4, data.length); // +2 bytes QTYPE + 2 bytes QCLASS
        return res;
    }

    /**
     * Builds an RFC 1035 compliant DNS response:
     * - A queries (IPv4) -> answers 0.0.0.0
     * - AAAA queries (IPv6) -> answers :: (16 zero bytes)
     * - HTTPS / SVCB queries -> returns NOERROR with 0 answers (NODATA)
     */
    private byte[] buildBlockedResponse(byte[] queryData, int questionEndOffset, int qType) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Transaction ID (bytes 0-1)
            baos.write(queryData[0]);
            baos.write(queryData[1]);

            // Flags (0x8180: Standard query response, Recursion Available, No Error)
            baos.write(0x81);
            baos.write(0x80);

            // If HTTPS / SVCB query (Type 65 or 64), return valid NODATA (ANCOUNT = 0)
            if (qType == 65 || qType == 64) {
                // QDCOUNT = 1
                baos.write(0x00); baos.write(0x01);
                // ANCOUNT = 0 (NODATA)
                baos.write(0x00); baos.write(0x00);
                // NSCOUNT = 0
                baos.write(0x00); baos.write(0x00);
                // ARCOUNT = 0
                baos.write(0x00); baos.write(0x00);

                int qLen = questionEndOffset - 12;
                if (qLen > 0 && questionEndOffset <= queryData.length) {
                    baos.write(queryData, 12, qLen);
                }
                return baos.toByteArray();
            }

            // QDCOUNT = 1
            baos.write(0x00);
            baos.write(0x01);

            // ANCOUNT = 1 (1 Answer record)
            baos.write(0x00);
            baos.write(0x01);

            // NSCOUNT = 0
            baos.write(0x00);
            baos.write(0x00);

            // ARCOUNT = 0
            baos.write(0x00);
            baos.write(0x00);

            // Copy Question section directly from query
            int qLen = questionEndOffset - 12;
            if (qLen > 0 && questionEndOffset <= queryData.length) {
                baos.write(queryData, 12, qLen);
            }

            // Answer Section:
            // Name: compression pointer to Question name (offset 0x0C = 12)
            baos.write(0xC0);
            baos.write(0x0C);

            if (qType == 28) {
                // Type: AAAA (0x001C) for IPv6 queries -> Sinkhole to ::
                baos.write(0x00);
                baos.write(0x1C);

                // Class: IN (0x0001)
                baos.write(0x00);
                baos.write(0x01);

                // TTL: 10 seconds (0x0000000A)
                baos.write(0x00); baos.write(0x00); baos.write(0x00); baos.write(0x0A);

                // RDLENGTH: 16 bytes (IPv6 ::)
                baos.write(0x00);
                baos.write(0x10);

                // RDATA: 16 zero bytes (::)
                for (int i = 0; i < 16; i++) {
                    baos.write(0x00);
                }
            } else {
                // Type: A (0x0001) for IPv4 queries -> Sinkhole to 0.0.0.0
                baos.write(0x00);
                baos.write(0x01);

                // Class: IN (0x0001)
                baos.write(0x00);
                baos.write(0x01);

                // TTL: 10 seconds (0x0000000A)
                baos.write(0x00); baos.write(0x00); baos.write(0x00); baos.write(0x0A);

                // RDLENGTH: 4 bytes (IPv4)
                baos.write(0x00);
                baos.write(0x04);

                // RDATA: 0.0.0.0
                baos.write(0x00); baos.write(0x00); baos.write(0x00); baos.write(0x00);
            }

            byte[] resp = baos.toByteArray();
            System.out.println("🔨 [DNS RESP BUILD] Constructed Sinkhole response (" + resp.length + " bytes, QType=" + qType + ")");
            return resp;
        } catch (Exception e) {
            System.err.println("❌ [DNS RESP BUILD FAIL] Error constructing blocked response: " + e.getMessage());
            return queryData;
        }
    }

    /**
     * Builds an immediate local PTR response for loopback and hotspot reverse lookups.
     */
    private byte[] buildPtrResponse(byte[] queryData, int questionEndOffset, String ptrTarget) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Transaction ID
            baos.write(queryData[0]);
            baos.write(queryData[1]);
            // Flags: 0x8180 (Standard response, No error, Recursion available)
            baos.write(0x81); baos.write(0x80);
            // QDCOUNT = 1, ANCOUNT = 1, NSCOUNT = 0, ARCOUNT = 0
            baos.write(0x00); baos.write(0x01);
            baos.write(0x00); baos.write(0x01);
            baos.write(0x00); baos.write(0x00);
            baos.write(0x00); baos.write(0x00);

            // Copy Question
            int qLen = questionEndOffset - 12;
            if (qLen > 0 && questionEndOffset <= queryData.length) {
                baos.write(queryData, 12, qLen);
            }

            // Answer: Pointer to Question QNAME (offset 12 = 0x0C)
            baos.write(0xC0); baos.write(0x0C);
            // Type PTR (12 = 0x000C)
            baos.write(0x00); baos.write(0x0C);
            // Class IN (1 = 0x0001)
            baos.write(0x00); baos.write(0x01);
            // TTL: 60s
            baos.write(0x00); baos.write(0x00); baos.write(0x00); baos.write(0x3C);

            // Encode Target Hostname in DNS format
            ByteArrayOutputStream rdata = new ByteArrayOutputStream();
            for (String part : ptrTarget.split("\\.")) {
                byte[] pb = part.getBytes();
                rdata.write(pb.length);
                rdata.write(pb);
            }
            rdata.write(0x00);
            byte[] rdataBytes = rdata.toByteArray();

            // RDLENGTH
            baos.write((rdataBytes.length >> 8) & 0xFF);
            baos.write(rdataBytes.length & 0xFF);
            baos.write(rdataBytes);

            byte[] resp = baos.toByteArray();
            System.out.println("🔨 [DNS RESP BUILD] Constructed Local PTR response (" + resp.length + " bytes -> " + ptrTarget + ")");
            return resp;
        } catch (Exception e) {
            System.err.println("❌ [DNS RESP BUILD FAIL] Error constructing PTR response: " + e.getMessage());
            return buildServFailResponse(queryData);
        }
    }

    /**
     * Builds a SERVFAIL response if upstream DNS is unreachable.
     */
    private byte[] buildServFailResponse(byte[] queryData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(queryData[0]);
            baos.write(queryData[1]);
            baos.write(0x81);
            baos.write(0x82); // 0x8182 = Server Failure
            baos.write(0x00); baos.write(0x01); // QDCOUNT
            baos.write(0x00); baos.write(0x00); // ANCOUNT
            baos.write(0x00); baos.write(0x00); // NSCOUNT
            baos.write(0x00); baos.write(0x00); // ARCOUNT
            if (queryData.length > 12) {
                baos.write(queryData, 12, queryData.length - 12);
            }
            byte[] resp = baos.toByteArray();
            System.out.println("🔨 [DNS RESP BUILD] Constructed SERVFAIL response (" + resp.length + " bytes)");
            return resp;
        } catch (Exception e) {
            return queryData;
        }
    }

    /**
     * Forwards non-blocked DNS queries to public upstream DNS servers with fallback.
     */
    private byte[] forwardToUpstreamDns(byte[] queryData) {
        for (String upstreamIp : UPSTREAM_SERVERS) {
            DatagramSocket upstreamSocket = null;
            try {
                upstreamSocket = new DatagramSocket();
                upstreamSocket.setSoTimeout(1200); // 1.2 second timeout per server

                InetAddress upstreamAddr = InetAddress.getByName(upstreamIp);
                DatagramPacket sendPacket = new DatagramPacket(queryData, queryData.length, upstreamAddr, UPSTREAM_PORT);
                upstreamSocket.send(sendPacket);

                byte[] recvBuffer = new byte[BUFFER_SIZE];
                DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
                upstreamSocket.receive(recvPacket);

                byte[] response = new byte[recvPacket.getLength()];
                System.arraycopy(recvPacket.getData(), 0, response, 0, recvPacket.getLength());
                System.out.println("🌐 [DNS UPSTREAM] " + upstreamIp + " answered with " + response.length + " bytes.");
                return response;
            } catch (SocketTimeoutException e) {
                System.out.println("⚠️ [DNS UPSTREAM TIMEOUT] Upstream server " + upstreamIp + " timed out, trying next...");
            } catch (Exception e) {
                System.out.println("⚠️ [DNS UPSTREAM ERROR] Upstream " + upstreamIp + ": " + e.getMessage() + ", trying next...");
            } finally {
                if (upstreamSocket != null && !upstreamSocket.isClosed()) {
                    upstreamSocket.close();
                }
            }
        }
        System.err.println("❌ [DNS UPSTREAM FAIL] All upstream DNS servers failed to respond.");
        return null;
    }

    private String resolveDeviceName(String clientIp) {
        if ("127.0.0.1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp)) {
            return "Local System (Test)";
        }
        try {
            List<Device> devices = deviceDAO.getAllDevices();
            for (Device d : devices) {
                if (clientIp.equalsIgnoreCase(d.getIpAddress())) {
                    return d.getDeviceName();
                }
            }
        } catch (Exception ignored) {}

        // Typical Windows Hotspot IP range
        if (clientIp.startsWith("192.168.137.")) {
            return "Hotspot Client (Phone B)";
        }
        return "Client (" + clientIp + ")";
    }

    public boolean isRunning() { return running; }
    public int getBoundPort() { return boundPort; }
    public String getStatusMessage() { return statusMessage; }
}
