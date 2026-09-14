package com.wifi.monitor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ApiHandler implements HttpHandler {

    private final String route;
    private final DeviceDAO deviceDAO = new DeviceDAO();
    private final UsageDAO usageDAO = new UsageDAO();
    private final RestrictionDAO restrictionDAO = new RestrictionDAO();
    private final BlockedLogDAO blockedLogDAO = new BlockedLogDAO();
    private final ReportGenerator reportGenerator = new ReportGenerator();
    private final AiAnomalyDetector aiDetector = new AiAnomalyDetector();

    public ApiHandler(String route) {
        this.route = route;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();
        String response;
        String contentType = "application/json";

        try {
            switch (route) {
                case "/api/login":        response = handleLogin(exchange, method); break;
                case "/api/register":     response = handleRegister(exchange, method); break;
                case "/api/dashboard":    response = handleDashboard(exchange, method); break;
                case "/api/devices":      response = handleDevices(exchange, method); break;
                case "/api/usage":        response = handleUsage(exchange, method); break;
                case "/api/history":      response = handleHistory(exchange, method); break;
                case "/api/report":       response = handleReport(exchange, method); break;
                case "/api/reports/export":
                    if ("csv".equalsIgnoreCase(parseQueryString(exchange.getRequestURI().getQuery()).get("format"))) {
                        contentType = "text/csv";
                        response = reportGenerator.generateCsvReport();
                    } else {
                        response = handleReportExport(exchange, method);
                    }
                    break;
                case "/api/alerts":          response = handleAlerts(exchange, method); break;
                case "/api/restrictions":    response = handleRestrictions(exchange, method); break;
                case "/api/blocking/events": response = handleBlockingEvents(exchange, method); break;
                case "/api/network/info":    response = handleNetworkInfo(exchange, method); break;
                case "/api/ai/anomalies":    response = handleAiAnomalies(exchange, method); break;
                default:                  response = "{\"error\":\"Unknown route\"}";
            }
        } catch (Exception e) {
            e.printStackTrace();
            response = "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }

        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── LOGIN ──────────────────────────────────────────────────────────────
    private String handleLogin(HttpExchange ex, String method) throws IOException {
        if (!"POST".equals(method)) return "{\"error\":\"POST required\"}";
        Map<String, String> body = parseBody(ex);
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");

        java.sql.Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return "{\"success\":false,\"message\":\"DB connection failed\"}";
        try {
            String q = "SELECT * FROM login WHERE username = ? AND password = ?";
            java.sql.PreparedStatement ps = conn.prepareStatement(q);
            ps.setString(1, username); ps.setString(2, password);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) return "{\"success\":true,\"username\":\"" + escapeJson(username) + "\"}";
            else           return "{\"success\":false,\"message\":\"Invalid credentials\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        } finally { try { conn.close(); } catch (Exception ignored) {} }
    }

    // ── REGISTER ────────────────────────────────────────────────────────────
    private String handleRegister(HttpExchange ex, String method) throws IOException {
        if (!"POST".equals(method)) return "{\"error\":\"POST required\"}";
        Map<String, String> body = parseBody(ex);
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        String email    = body.getOrDefault("email", "");

        if (username.isEmpty() || password.isEmpty() || email.isEmpty())
            return "{\"success\":false,\"message\":\"All fields required\"}";

        java.sql.Connection conn = DatabaseConnection.getConnection();
        if (conn == null) return "{\"success\":false,\"message\":\"DB connection failed\"}";
        try {
            java.sql.PreparedStatement check = conn.prepareStatement("SELECT * FROM login WHERE username = ?");
            check.setString(1, username);
            if (check.executeQuery().next()) return "{\"success\":false,\"message\":\"Username already exists\"}";

            java.sql.PreparedStatement ps = conn.prepareStatement("INSERT INTO login (username, password, email) VALUES (?, ?, ?)");
            ps.setString(1, username); ps.setString(2, password); ps.setString(3, email);
            ps.executeUpdate();
            return "{\"success\":true,\"message\":\"Account created successfully\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        } finally { try { conn.close(); } catch (Exception ignored) {} }
    }

    // ── DASHBOARD STATS & BREAKDOWN ──────────────────────────────────────────
    private String handleDashboard(HttpExchange ex, String method) {
        List<Device> devices = deviceDAO.getAllDevices();
        Map<String, Double> traffic = usageDAO.getOverallTrafficSplit();
        double todayUsage = usageDAO.getTodayTotalUsage();

        int connectedCount = 0;
        int disconnectedCount = 0;
        double totalUpload = traffic.getOrDefault("upload", 0.0);
        double totalDownload = traffic.getOrDefault("download", 0.0);
        double totalDataUsed = traffic.getOrDefault("total", 0.0);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDataUsed\":").append(String.format("%.2f", totalDataUsed)).append(",");
        sb.append("\"uploadUsage\":").append(String.format("%.2f", totalUpload)).append(",");
        sb.append("\"downloadUsage\":").append(String.format("%.2f", totalDownload)).append(",");
        sb.append("\"todayUsage\":").append(String.format("%.2f", todayUsage)).append(",");
        sb.append("\"totalDevicesCount\":").append(devices.size()).append(",");

        StringBuilder deviceListSb = new StringBuilder("[");
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            if ("Connected".equalsIgnoreCase(d.getStatus())) connectedCount++;
            else disconnectedCount++;

            double devTotal = d.getUploadGb() + d.getDownloadGb();
            if (devTotal == 0) devTotal = usageDAO.getTotalUsageByDevice(d.getDeviceId());

            deviceListSb.append("{")
                .append("\"deviceId\":").append(d.getDeviceId()).append(",")
                .append("\"deviceName\":\"").append(escapeJson(d.getDeviceName())).append("\",")
                .append("\"ownerName\":\"").append(escapeJson(d.getOwnerName())).append("\",")
                .append("\"ipAddress\":\"").append(escapeJson(d.getIpAddress())).append("\",")
                .append("\"macAddress\":\"").append(escapeJson(d.getMacAddress())).append("\",")
                .append("\"uploadGb\":").append(String.format("%.2f", d.getUploadGb())).append(",")
                .append("\"downloadGb\":").append(String.format("%.2f", d.getDownloadGb())).append(",")
                .append("\"totalUsage\":").append(String.format("%.2f", devTotal)).append(",")
                .append("\"dataLimit\":").append(d.getDataLimit()).append(",")
                .append("\"isBlocked\":").append(d.isBlocked())
                .append("}");
            if (i < devices.size() - 1) deviceListSb.append(",");
        }
        deviceListSb.append("]");

        sb.append("\"connectedDevicesCount\":").append(connectedCount).append(",");
        sb.append("\"disconnectedDevicesCount\":").append(disconnectedCount).append(",");
        sb.append("\"deviceUsageBreakdown\":").append(deviceListSb.toString());
        sb.append("}");

        return sb.toString();
    }

    // ── DEVICES ─────────────────────────────────────────────────────────────
    private String handleDevices(HttpExchange ex, String method) throws IOException {
        if ("GET".equals(method)) {
            List<Device> devices = deviceDAO.getAllDevices();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < devices.size(); i++) {
                Device d = devices.get(i);
                double devTotal = d.getUploadGb() + d.getDownloadGb();
                if (devTotal == 0) devTotal = usageDAO.getTotalUsageByDevice(d.getDeviceId());

                sb.append("{")
                  .append("\"deviceId\":").append(d.getDeviceId()).append(",")
                  .append("\"deviceName\":\"").append(escapeJson(d.getDeviceName())).append("\",")
                  .append("\"ownerName\":\"").append(escapeJson(d.getOwnerName())).append("\",")
                  .append("\"deviceType\":\"").append(escapeJson(d.getDeviceType())).append("\",")
                  .append("\"dataLimit\":").append(d.getDataLimit()).append(",")
                  .append("\"ipAddress\":\"").append(escapeJson(d.getIpAddress())).append("\",")
                  .append("\"macAddress\":\"").append(escapeJson(d.getMacAddress())).append("\",")
                  .append("\"status\":\"").append(escapeJson(d.getStatus())).append("\",")
                  .append("\"isBlocked\":").append(d.isBlocked()).append(",")
                  .append("\"uploadGb\":").append(String.format("%.2f", d.getUploadGb())).append(",")
                  .append("\"downloadGb\":").append(String.format("%.2f", d.getDownloadGb())).append(",")
                  .append("\"totalUsage\":").append(String.format("%.2f", devTotal)).append(",")
                  .append("\"bandwidthLimitMbps\":").append(d.getBandwidthLimitMbps()).append(",")
                  .append("\"blockedWebsites\":\"").append(escapeJson(d.getBlockedWebsites())).append("\"")
                  .append("}");
                if (i < devices.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();

        } else if ("POST".equals(method)) {
            Map<String, String> body = parseBody(ex);
            String name  = body.getOrDefault("deviceName", "");
            String owner = body.getOrDefault("ownerName", "");
            String type  = body.getOrDefault("deviceType", "Other");
            String ip    = body.getOrDefault("ipAddress", "192.168.1." + (100 + new Random().nextInt(150)));
            String mac   = body.getOrDefault("macAddress", generateRandomMac());
            double limit;
            try { limit = Double.parseDouble(body.getOrDefault("dataLimit", "50")); }
            catch (NumberFormatException e) { return "{\"success\":false,\"message\":\"Invalid data limit\"}"; }

            Device d = new Device(0, name, owner, type, limit, ip, mac, "Connected", false, 0.0, 0.0, 100.0, "");
            deviceDAO.addDevice(d);
            return "{\"success\":true,\"message\":\"Device registered successfully\"}";

        } else if ("PUT".equals(method)) {
            Map<String, String> body = parseBody(ex);
            String action = body.getOrDefault("action", "");
            int id;
            try { id = Integer.parseInt(body.getOrDefault("deviceId", "-1")); }
            catch (NumberFormatException e) { return "{\"success\":false,\"message\":\"Invalid ID\"}"; }

            if ("block".equalsIgnoreCase(action) || "unblock".equalsIgnoreCase(action)) {
                boolean block = "block".equalsIgnoreCase(action);
                deviceDAO.toggleBlockDevice(id, block);
                return "{\"success\":true,\"message\":\"Device block state updated\"}";
            } else if ("set_limits".equalsIgnoreCase(action)) {
                double dataLimit = Double.parseDouble(body.getOrDefault("dataLimit", "50"));
                double speedLimit = Double.parseDouble(body.getOrDefault("speedLimit", "100"));
                deviceDAO.setDeviceLimits(id, dataLimit, speedLimit);
                return "{\"success\":true,\"message\":\"Device limits updated\"}";
            } else if ("toggle_status".equalsIgnoreCase(action)) {
                String newStatus = body.getOrDefault("status", "Connected");
                deviceDAO.updateStatus(id, newStatus);
                return "{\"success\":true,\"message\":\"Device status updated\"}";
            } else if ("update_websites".equalsIgnoreCase(action)) {
                String websites = body.getOrDefault("blockedWebsites", "");
                deviceDAO.updateBlockedWebsites(id, websites);
                return "{\"success\":true,\"message\":\"Blocked websites updated\"}";
            }
            return "{\"success\":false,\"message\":\"Unsupported action\"}";

        } else if ("DELETE".equals(method)) {
            Map<String, String> params = parseQueryString(ex.getRequestURI().getQuery());
            int id;
            try { id = Integer.parseInt(params.getOrDefault("id", "-1")); }
            catch (NumberFormatException e) { return "{\"success\":false,\"message\":\"Invalid ID\"}"; }
            deviceDAO.removeDevice(id);
            return "{\"success\":true,\"message\":\"Device deleted\"}";
        }
        return "{\"error\":\"Method not supported\"}";
    }

    // ── USAGE ───────────────────────────────────────────────────────────────
    private String handleUsage(HttpExchange ex, String method) throws IOException {
        if ("GET".equals(method)) {
            List<UsageRecord> records = usageDAO.getAllUsageRecords();
            List<Device> devices = deviceDAO.getAllDevices();
            Map<Integer, String> deviceNames = new HashMap<>();
            for (Device d : devices) deviceNames.put(d.getDeviceId(), d.getDeviceName() + " (" + d.getOwnerName() + ")");

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < records.size(); i++) {
                UsageRecord r = records.get(i);
                String info = deviceNames.getOrDefault(r.getDeviceId(), "Unknown");
                sb.append("{")
                  .append("\"usageId\":").append(r.getUsageId()).append(",")
                  .append("\"deviceId\":").append(r.getDeviceId()).append(",")
                  .append("\"deviceInfo\":\"").append(escapeJson(info)).append("\",")
                  .append("\"usageDate\":\"").append(escapeJson(r.getUsageDate())).append("\",")
                  .append("\"uploadGb\":").append(String.format("%.2f", r.getUploadGb())).append(",")
                  .append("\"downloadGb\":").append(String.format("%.2f", r.getDownloadGb())).append(",")
                  .append("\"dataUsed\":").append(String.format("%.2f", r.getDataUsed()))
                  .append("}");
                if (i < records.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        } else if ("POST".equals(method)) {
            Map<String, String> body = parseBody(ex);
            int deviceId;
            double dataUsed, uploadGb = 0, downloadGb = 0;
            try {
                deviceId = Integer.parseInt(body.getOrDefault("deviceId", "-1"));
                dataUsed = Double.parseDouble(body.getOrDefault("dataUsed", "0"));
                if (body.containsKey("uploadGb")) uploadGb = Double.parseDouble(body.get("uploadGb"));
                if (body.containsKey("downloadGb")) downloadGb = Double.parseDouble(body.get("downloadGb"));
            } catch (NumberFormatException e) { return "{\"success\":false,\"message\":\"Invalid numbers\"}"; }
            String date = body.getOrDefault("usageDate", java.time.LocalDate.now().toString());

            if (uploadGb == 0 && downloadGb == 0) {
                uploadGb = dataUsed * 0.25;
                downloadGb = dataUsed * 0.75;
            }
            usageDAO.addUsageRecord(deviceId, date, dataUsed, uploadGb, downloadGb);
            return "{\"success\":true,\"message\":\"Usage logged successfully\"}";
        }
        return "{\"error\":\"Method not supported\"}";
    }

    // ── USAGE HISTORY (DAILY/WEEKLY/MONTHLY GRAPHS) ──────────────────────────
    private String handleHistory(HttpExchange ex, String method) {
        List<Map<String, Object>> dailyStats = usageDAO.getDailyStats(14);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dailyStats.size(); i++) {
            Map<String, Object> row = dailyStats.get(i);
            sb.append("{")
              .append("\"date\":\"").append(escapeJson((String) row.get("date"))).append("\",")
              .append("\"upload\":").append(String.format("%.2f", (Double) row.get("upload"))).append(",")
              .append("\"download\":").append(String.format("%.2f", (Double) row.get("download"))).append(",")
              .append("\"total\":").append(String.format("%.2f", (Double) row.get("total")))
              .append("}");
            if (i < dailyStats.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── REPORT ──────────────────────────────────────────────────────────────
    private String handleReport(HttpExchange ex, String method) {
        Map<String, String> params = parseQueryString(ex.getRequestURI().getQuery());
        String date = params.getOrDefault("date", java.time.LocalDate.now().toString());
        String reportText = usageDAO.getDailyReportText(date);
        return "{\"report\":\"" + escapeJson(reportText) + "\"}";
    }

    private String handleReportExport(HttpExchange ex, String method) {
        String csv = reportGenerator.generateCsvReport();
        return "{\"success\":true,\"csv\":\"" + escapeJson(csv) + "\"}";
    }

    // ── ALERTS ──────────────────────────────────────────────────────────────
    private String handleAlerts(HttpExchange ex, String method) {
        List<String> alerts = reportGenerator.getLimitAlerts();
        double totalUsage = reportGenerator.getTotalConsumption();
        int deviceCount = deviceDAO.getAllDevices().size();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDevices\":").append(deviceCount).append(",");
        sb.append("\"totalUsage\":").append(String.format("%.2f", totalUsage)).append(",");
        sb.append("\"alertCount\":").append(alerts.size()).append(",");
        sb.append("\"alerts\":[");
        for (int i = 0; i < alerts.size(); i++) {
            sb.append("\"").append(escapeJson(alerts.get(i))).append("\"");
            if (i < alerts.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── WEBSITE RESTRICTIONS & BLOCKING ─────────────────────────────────────
    private String handleRestrictions(HttpExchange ex, String method) throws IOException {
        if ("GET".equals(method)) {
            List<WebsiteRestriction> list = restrictionDAO.getAllRestrictions();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                WebsiteRestriction r = list.get(i);
                sb.append("{")
                  .append("\"restrictionId\":").append(r.getRestrictionId()).append(",")
                  .append("\"deviceId\":").append(r.getDeviceId()).append(",")
                  .append("\"domainName\":\"").append(escapeJson(r.getDomainName())).append("\",")
                  .append("\"category\":\"").append(escapeJson(r.getCategory())).append("\",")
                  .append("\"action\":\"").append(escapeJson(r.getAction())).append("\",")
                  .append("\"status\":\"").append(escapeJson(r.getStatus())).append("\",")
                  .append("\"createdAt\":\"").append(escapeJson(r.getCreatedAt())).append("\"")
                  .append("}");
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        } else if ("POST".equals(method)) {
            Map<String, String> body = parseBody(ex);
            int deviceId = Integer.parseInt(body.getOrDefault("deviceId", "0"));
            String domain = body.getOrDefault("domainName", "").trim();
            String cat    = body.getOrDefault("category", "General");
            String action = body.getOrDefault("action", "BLOCK");
            String status = body.getOrDefault("status", "BLOCKED");

            if (domain.isEmpty()) return "{\"success\":false,\"message\":\"Domain name required\"}";

            restrictionDAO.addRestriction(new WebsiteRestriction(0, deviceId, domain, cat, action, status, null));
            return "{\"success\":true,\"message\":\"Domain " + escapeJson(domain) + " added to blocklist\"}";
        } else if ("PUT".equals(method)) {
            Map<String, String> body = parseBody(ex);
            int id = Integer.parseInt(body.getOrDefault("id", body.getOrDefault("restrictionId", "-1")));
            String newStatus = body.getOrDefault("status", "BLOCKED");

            if (id > 0) {
                restrictionDAO.toggleRestrictionStatus(id, newStatus);
                return "{\"success\":true,\"message\":\"Domain status updated to " + escapeJson(newStatus) + "\"}";
            } else if (body.containsKey("domainName")) {
                String domain = body.get("domainName");
                restrictionDAO.setDomainStatus(domain, newStatus);
                return "{\"success\":true,\"message\":\"Domain " + escapeJson(domain) + " set to " + escapeJson(newStatus) + "\"}";
            }
            return "{\"success\":false,\"message\":\"Missing id or domainName\"}";
        } else if ("DELETE".equals(method)) {
            Map<String, String> params = parseQueryString(ex.getRequestURI().getQuery());
            int id = Integer.parseInt(params.getOrDefault("id", "-1"));
            restrictionDAO.removeRestriction(id);
            return "{\"success\":true,\"message\":\"Restriction removed\"}";
        }
        return "{\"error\":\"Method not supported\"}";
    }

    // ── REAL-TIME BLOCKED REQUEST EVENTS ────────────────────────────────────
    private String handleBlockingEvents(HttpExchange ex, String method) throws IOException {
        if ("GET".equals(method)) {
            Map<String, String> params = parseQueryString(ex.getRequestURI().getQuery());
            int limit = Integer.parseInt(params.getOrDefault("limit", "50"));
            List<BlockedLogDAO.BlockedEvent> logs = blockedLogDAO.getRecentLogs(limit);

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < logs.size(); i++) {
                BlockedLogDAO.BlockedEvent ev = logs.get(i);
                sb.append("{")
                  .append("\"logId\":").append(ev.getLogId()).append(",")
                  .append("\"domainName\":\"").append(escapeJson(ev.getDomainName())).append("\",")
                  .append("\"clientIp\":\"").append(escapeJson(ev.getClientIp())).append("\",")
                  .append("\"deviceName\":\"").append(escapeJson(ev.getDeviceName())).append("\",")
                  .append("\"timestamp\":\"").append(escapeJson(ev.getTimestamp())).append("\",")
                  .append("\"action\":\"").append(escapeJson(ev.getAction())).append("\"")
                  .append("}");
                if (i < logs.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        } else if ("POST".equals(method)) {
            Map<String, String> body = parseBody(ex);
            String action = body.getOrDefault("action", "simulate");
            if ("simulate".equalsIgnoreCase(action)) {
                String domain = body.getOrDefault("domain", "youtube.com");
                String ip = body.getOrDefault("ip", "192.168.137.45");
                String dev = body.getOrDefault("device", "Phone B (Test Device)");
                BlockedLogDAO.BlockedEvent ev = blockedLogDAO.recordBlockedRequest(domain, ip, dev, "BLOCKED");
                return "{\"success\":true,\"message\":\"Simulated block logged\",\"domain\":\"" + escapeJson(domain) + "\"}";
            } else if ("clear".equalsIgnoreCase(action)) {
                blockedLogDAO.clearLogs();
                return "{\"success\":true,\"message\":\"Logs cleared\"}";
            }
        }
        return "{\"error\":\"Method not supported\"}";
    }

    // ── NETWORK INTERCEPTOR INFO ────────────────────────────────────────────
    private String handleNetworkInfo(HttpExchange ex, String method) {
        String localIp = "127.0.0.1";
        String hotspotIp = "192.168.137.1";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.137.")) {
                            hotspotIp = ip;
                        } else if (!ip.startsWith("127.")) {
                            localIp = ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        boolean dnsRunning = ApiServer.dnsFilterServer.isRunning();
        int dnsPort = ApiServer.dnsFilterServer.getBoundPort();
        String dnsStatus = ApiServer.dnsFilterServer.getStatusMessage();

        boolean proxyRunning = ApiServer.httpFilterProxy.isRunning();
        int proxyPort = ApiServer.httpFilterProxy.getPort();
        String proxyStatus = ApiServer.httpFilterProxy.getStatusMessage();

        return "{"
                + "\"localIp\":\"" + escapeJson(localIp) + "\","
                + "\"hotspotIp\":\"" + escapeJson(hotspotIp) + "\","
                + "\"dnsServer\":{\"running\":" + dnsRunning + ",\"port\":" + dnsPort + ",\"status\":\"" + escapeJson(dnsStatus) + "\"},"
                + "\"proxyServer\":{\"running\":" + proxyRunning + ",\"port\":" + proxyPort + ",\"status\":\"" + escapeJson(proxyStatus) + "\"}"
                + "}";
    }

    // ── AI ABNORMAL USAGE DETECTION ENGINE ──────────────────────────────────
    private String handleAiAnomalies(HttpExchange ex, String method) {
        List<AiAnomalyDetector.AnomalyResult> anomalies = aiDetector.detectAnomalies();
        int networkRiskScore = aiDetector.calculateOverallNetworkRiskScore(anomalies);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"networkRiskScore\":").append(networkRiskScore).append(",");
        sb.append("\"anomalyCount\":").append(anomalies.size()).append(",");
        sb.append("\"anomalies\":[");
        for (int i = 0; i < anomalies.size(); i++) {
            AiAnomalyDetector.AnomalyResult a = anomalies.get(i);
            sb.append("{")
              .append("\"deviceId\":").append(a.deviceId).append(",")
              .append("\"deviceName\":\"").append(escapeJson(a.deviceName)).append("\",")
              .append("\"macAddress\":\"").append(escapeJson(a.macAddress)).append("\",")
              .append("\"ipAddress\":\"").append(escapeJson(a.ipAddress)).append("\",")
              .append("\"severity\":\"").append(escapeJson(a.severity)).append("\",")
              .append("\"anomalyType\":\"").append(escapeJson(a.anomalyType)).append("\",")
              .append("\"riskScore\":").append(a.riskScore).append(",")
              .append("\"aiExplanation\":\"").append(escapeJson(a.aiExplanation)).append("\",")
              .append("\"suggestedAction\":\"").append(escapeJson(a.suggestedAction)).append("\"")
              .append("}");
            if (i < anomalies.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────
    private Map<String, String> parseBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        Map<String, String> map = new HashMap<>();
        if (body.isEmpty()) return map;

        // If JSON payload (e.g. {"domainName":"youtube.com"})
        if (body.startsWith("{") && body.endsWith("}")) {
            String inner = body.substring(1, body.length() - 1).trim();
            String[] pairs = inner.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String p : pairs) {
                String[] kv = p.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(k, v);
                }
            }
            return map;
        }

        return parseQueryString(body);
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private String generateRandomMac() {
        Random r = new Random();
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                r.nextInt(256), r.nextInt(256), r.nextInt(256),
                r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
