package com.wifi.monitor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP & HTTPS Forwarding Proxy (Port 8081) that inspects
 * CONNECT and HTTP requests from devices configured with the laptop's proxy.
 * 
 * - If target host is in Blocked List: Responds with HTTP 403 Forbidden & logs real-time event.
 * - If target host is Allowed: Tunnels traffic bidirectionally.
 */
public class HttpFilterProxy implements Runnable {

    private static final int PROXY_PORT = 8081;
    private final RestrictionDAO restrictionDAO = new RestrictionDAO();
    private final BlockedLogDAO blockedLogDAO = new BlockedLogDAO();
    private final DeviceDAO deviceDAO = new DeviceDAO();

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private String statusMessage = "Initializing";

    public void start() {
        if (running) return;
        Thread thread = new Thread(this, "HttpFilterProxy-Worker");
        thread.setDaemon(false);
        thread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        threadPool.shutdownNow();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PROXY_PORT);
            running = true;
            statusMessage = "Active on Port " + PROXY_PORT;
            System.out.println("🛡️ HTTP/HTTPS Filter Proxy running on port " + PROXY_PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            running = false;
            statusMessage = "Failed to bind: " + e.getMessage();
            System.out.println("⚠️ HTTP/HTTPS Filter Proxy could not bind port " + PROXY_PORT + ": " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            clientSocket.setSoTimeout(10000);
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn, StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                clientSocket.close();
                return;
            }

            String method = parts[0].toUpperCase();
            String uri = parts[1];
            String targetHost = "";
            int targetPort = 80;

            if ("CONNECT".equals(method)) {
                // HTTPS Tunnel: CONNECT domain.com:443 HTTP/1.1
                String[] hostPort = uri.split(":");
                targetHost = hostPort[0];
                targetPort = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 443;
            } else {
                // HTTP Request: GET http://domain.com/path HTTP/1.1
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    URL url = new URL(uri);
                    targetHost = url.getHost();
                    targetPort = url.getPort() != -1 ? url.getPort() : (uri.startsWith("https") ? 443 : 80);
                } else {
                    targetHost = uri;
                }
            }

            // Read headers to check for Host header if targetHost is still empty
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (targetHost.isEmpty() && line.toLowerCase().startsWith("host:")) {
                    String val = line.substring(5).trim();
                    targetHost = val.contains(":") ? val.split(":")[0] : val;
                }
            }

            String clientIp = clientSocket.getInetAddress().getHostAddress();
            deviceDAO.registerDiscoveredClient(clientIp);
            String deviceName = resolveDeviceName(clientIp);

            // 1. DEVICE BLOCKING ENFORCEMENT
            if (deviceDAO.isIpBlocked(clientIp)) {
                String body = "403 Forbidden: Device (" + clientIp + ") is blocked from Wi-Fi access by network administrator.\r\n";
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                String header = "HTTP/1.1 403 Forbidden\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n"
                        + "Connection: close\r\n\r\n";

                clientOut.write(header.getBytes(StandardCharsets.US_ASCII));
                clientOut.write(bodyBytes);
                clientOut.flush();

                blockedLogDAO.recordBlockedRequest("ALL TRAFFIC (DEVICE BLOCKED)", clientIp, deviceName, "DEVICE BLOCKED");
                clientSocket.close();
                return;
            }

            // 2. Check if target website domain is blocked
            if (restrictionDAO.isDomainBlocked(targetHost)) {
                String body = "403 Forbidden: Access to " + targetHost + " is restricted by network administrator.\r\n";
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                String header = "HTTP/1.1 403 Forbidden\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n"
                        + "Connection: close\r\n\r\n";

                clientOut.write(header.getBytes(StandardCharsets.US_ASCII));
                clientOut.write(bodyBytes);
                clientOut.flush();

                // Log real-time event
                blockedLogDAO.recordBlockedRequest(targetHost, clientIp, deviceName, "BLOCKED");

                clientSocket.close();
                return;
            }

            // ALLOWED -> Tunnel / Forward connection
            Socket targetSocket = new Socket(targetHost, targetPort);
            clientSocket.setSoTimeout(0);
            targetSocket.setSoTimeout(0);

            if ("CONNECT".equals(method)) {
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                clientOut.flush();
            }

            // Pipe streams in background
            InputStream targetIn = targetSocket.getInputStream();
            OutputStream targetOut = targetSocket.getOutputStream();

            threadPool.submit(() -> pipe(clientIn, targetOut, clientSocket, targetSocket));
            threadPool.submit(() -> pipe(targetIn, clientOut, targetSocket, clientSocket));

        } catch (Exception e) {
            try { clientSocket.close(); } catch (Exception ignored) {}
        }
    }

    private void pipe(InputStream in, OutputStream out, Socket s1, Socket s2) {
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            try { s1.close(); } catch (Exception ignored) {}
            try { s2.close(); } catch (Exception ignored) {}
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

        if (clientIp.startsWith("192.168.137.")) {
            return "Hotspot Client (Phone B)";
        }
        return "Client (" + clientIp + ")";
    }

    public boolean isRunning() { return running; }
    public int getPort() { return PROXY_PORT; }
    public String getStatusMessage() { return statusMessage; }
}
