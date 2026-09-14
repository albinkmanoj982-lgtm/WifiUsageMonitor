package com.wifi.monitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.List;

/**
 * Lightweight UDP DNS Server (Port 53) that intercepts DNS queries from
 * devices connected to the laptop's Mobile Hotspot.
 * 
 * - If domain is in Blocked List: Responds with 0.0.0.0 (Sinkhole) & logs real-time event.
 * - If domain is Allowed: Relays query to Upstream DNS (1.1.1.1) and returns real answer.
 */
public class DnsFilterServer implements Runnable {

    private static final int DNS_PORT = 53;
    private static final String UPSTREAM_DNS = "1.1.1.1";
    private static final int UPSTREAM_PORT = 53;
    private static final int BUFFER_SIZE = 1024;

    private final RestrictionDAO restrictionDAO = new RestrictionDAO();
    private final BlockedLogDAO blockedLogDAO = new BlockedLogDAO();
    private final DeviceDAO deviceDAO = new DeviceDAO();

    private DatagramSocket socket;
    private volatile boolean running = false;
    private int boundPort = DNS_PORT;
    private String statusMessage = "Initializing";

    public void start() {
        if (running) return;
        Thread thread = new Thread(this, "DnsFilterServer-Worker");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        try {
            // Attempt to bind standard UDP port 53
            socket = new DatagramSocket(DNS_PORT);
            boundPort = DNS_PORT;
            running = true;
            statusMessage = "Active on UDP Port " + DNS_PORT;
            System.out.println("🛡️ DNS Filter Server running on UDP port " + DNS_PORT);
        } catch (SocketException e) {
            // If port 53 is restricted or held by Windows ICS, attempt fallback port 5300
            try {
                boundPort = 5300;
                socket = new DatagramSocket(boundPort);
                running = true;
                statusMessage = "Active on UDP Port 5300 (Port 53 in use)";
                System.out.println("⚠️ DNS Filter Server fallback active on UDP port " + boundPort + " (Port 53 occupied or requires Admin)");
            } catch (SocketException ex) {
                running = false;
                statusMessage = "Failed to bind: " + e.getMessage();
                System.out.println("⚠️ DNS Filter Server could not bind port 53 or 5300: " + e.getMessage());
                return;
            }
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Handle DNS request asynchronously in a worker thread
                final byte[] queryData = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), queryData, 0, packet.getLength());
                final InetAddress clientAddress = packet.getAddress();
                final int clientPort = packet.getPort();

                new Thread(() -> handleDnsQuery(queryData, clientAddress, clientPort)).start();

            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    private void handleDnsQuery(byte[] queryData, InetAddress clientAddress, int clientPort) {
        if (queryData.length < 12) return; // Malformed packet

        try {
            // 1. Parse requested domain name from Question section
            ParsedDnsQuery parsed = parseDomainName(queryData);
            String domain = parsed.domain;

            if (domain != null && !domain.isEmpty()) {
                // 2. Check if domain is in the active blocklist
                if (restrictionDAO.isDomainBlocked(domain)) {
                    // BLOCKED -> Build spoofed response pointing to 0.0.0.0 (IPv4) or :: (IPv6)
                    byte[] blockedResponse = buildBlockedResponse(queryData, parsed.questionEndOffset, parsed.qType);
                    DatagramPacket responsePacket = new DatagramPacket(blockedResponse, blockedResponse.length, clientAddress, clientPort);
                    socket.send(responsePacket);

                    // Record real-time event
                    String clientIp = clientAddress.getHostAddress();
                    String deviceName = resolveDeviceName(clientIp);
                    blockedLogDAO.recordBlockedRequest(domain, clientIp, deviceName, "BLOCKED");
                    return;
                }
            }

            // 3. ALLOWED -> Forward to upstream DNS (1.1.1.1)
            byte[] upstreamResponse = forwardToUpstreamDns(queryData);
            if (upstreamResponse != null && upstreamResponse.length > 0) {
                DatagramPacket responsePacket = new DatagramPacket(upstreamResponse, upstreamResponse.length, clientAddress, clientPort);
                socket.send(responsePacket);
            }

        } catch (Exception e) {
            // Ignore transient packet parse errors
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

            return baos.toByteArray();
        } catch (Exception e) {
            return queryData;
        }
    }

    /**
     * Forwards non-blocked DNS queries to public upstream DNS (1.1.1.1)
     */
    private byte[] forwardToUpstreamDns(byte[] queryData) {
        DatagramSocket upstreamSocket = null;
        try {
            upstreamSocket = new DatagramSocket();
            upstreamSocket.setSoTimeout(2500); // 2.5 second timeout

            InetAddress upstreamAddr = InetAddress.getByName(UPSTREAM_DNS);
            DatagramPacket sendPacket = new DatagramPacket(queryData, queryData.length, upstreamAddr, UPSTREAM_PORT);
            upstreamSocket.send(sendPacket);

            byte[] recvBuffer = new byte[BUFFER_SIZE];
            DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
            upstreamSocket.receive(recvPacket);

            byte[] response = new byte[recvPacket.getLength()];
            System.arraycopy(recvPacket.getData(), 0, response, 0, recvPacket.getLength());
            return response;
        } catch (Exception e) {
            return null;
        } finally {
            if (upstreamSocket != null && !upstreamSocket.isClosed()) {
                upstreamSocket.close();
            }
        }
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
