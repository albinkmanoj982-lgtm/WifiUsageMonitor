package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class RestrictionDAO {

    private static final List<WebsiteRestriction> inMemoryRestrictions = new CopyOnWriteArrayList<>();
    private static final AtomicInteger restrictionIdCounter = new AtomicInteger(10);

    // Common service CDNs and streaming endpoints mapping so blocking "youtube.com" blocks video streaming endpoints
    private static final java.util.Map<String, List<String>> SERVICE_ALIASES = new java.util.HashMap<>();

    static {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        inMemoryRestrictions.add(new WebsiteRestriction(1, 0, "youtube.com", "Streaming", "BLOCK", "BLOCKED", now));
        inMemoryRestrictions.add(new WebsiteRestriction(2, 0, "gambling-site.com", "Gambling", "BLOCK", "BLOCKED", now));
        inMemoryRestrictions.add(new WebsiteRestriction(3, 0, "malware-host.net", "Security Risk", "BLOCK", "BLOCKED", now));
        inMemoryRestrictions.add(new WebsiteRestriction(4, 0, "tiktok.com", "Social Media", "BLOCK", "BLOCKED", now));
        inMemoryRestrictions.add(new WebsiteRestriction(5, 0, "torrent-downloads.org", "P2P File Sharing", "BLOCK", "BLOCKED", now));

        // YouTube ecosystem (Player, CDN streaming, Thumbnails, API)
        List<String> ytList = java.util.Arrays.asList(
            "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com",
            "youtube-nocookie.com", "youtubei.googleapis.com", "yt3.ggpht.com"
        );
        SERVICE_ALIASES.put("youtube.com", ytList);
        SERVICE_ALIASES.put("youtu.be", ytList);

        // Instagram ecosystem
        List<String> igList = java.util.Arrays.asList("instagram.com", "cdninstagram.com", "ig.me");
        SERVICE_ALIASES.put("instagram.com", igList);

        // Facebook ecosystem
        List<String> fbList = java.util.Arrays.asList("facebook.com", "fb.com", "fbcdn.net", "fbsbx.com");
        SERVICE_ALIASES.put("facebook.com", fbList);
        SERVICE_ALIASES.put("fb.com", fbList);

        // TikTok ecosystem
        List<String> ttList = java.util.Arrays.asList("tiktok.com", "tiktokcdn.com", "byteoversea.com", "ibytedtos.com");
        SERVICE_ALIASES.put("tiktok.com", ttList);

        // Twitter / X ecosystem
        List<String> twList = java.util.Arrays.asList("twitter.com", "x.com", "twimg.com", "t.co");
        SERVICE_ALIASES.put("twitter.com", twList);
        SERVICE_ALIASES.put("x.com", twList);

        // Netflix ecosystem
        List<String> nfList = java.util.Arrays.asList("netflix.com", "nflxvideo.net", "nflximg.net", "nflxext.com");
        SERVICE_ALIASES.put("netflix.com", nfList);

        // Spotify ecosystem
        List<String> spList = java.util.Arrays.asList("spotify.com", "scdn.co");
        SERVICE_ALIASES.put("spotify.com", spList);
    }

    public static String cleanDomain(String raw) {
        if (raw == null) return "";
        String d = raw.toLowerCase().trim();
        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);
        if (d.startsWith("ftp://")) d = d.substring(6);
        if (d.contains("/")) d = d.substring(0, d.indexOf("/"));
        if (d.contains(":")) d = d.substring(0, d.indexOf(":"));
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (d.startsWith("www.")) d = d.substring(4);
        return d.trim();
    }

    public boolean isDomainBlocked(String queryDomain) {
        if (queryDomain == null || queryDomain.trim().isEmpty()) return false;

        String domain = cleanDomain(queryDomain);
        if (domain.isEmpty()) return false;

        List<WebsiteRestriction> list = getAllRestrictions();
        for (WebsiteRestriction r : list) {
            if (!r.isBlocked()) continue;

            String blocked = cleanDomain(r.getDomainName());
            if (blocked.isEmpty()) continue;

            // 1. Direct match (e.g. youtube.com == youtube.com)
            if (domain.equals(blocked)) return true;

            // 2. Subdomain match (e.g. m.youtube.com ends with .youtube.com)
            if (domain.endsWith("." + blocked)) return true;

            // 3. Reverse subdomain match (if rule was entered as www.something.com)
            if (blocked.endsWith("." + domain)) return true;

            // 4. Ecosystem & CDN aliases (e.g. googlevideo.com or ytimg.com when youtube.com is blocked)
            List<String> aliases = SERVICE_ALIASES.get(blocked);
            if (aliases != null) {
                for (String alias : aliases) {
                    String cleanAlias = cleanDomain(alias);
                    if (domain.equals(cleanAlias) || domain.endsWith("." + cleanAlias)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void addRestriction(WebsiteRestriction restriction) {
        if (restriction.getStatus() == null || restriction.getStatus().isEmpty()) {
            restriction.setStatus("BLOCKED");
        }
        if (restriction.getCreatedAt() == null || restriction.getCreatedAt().isEmpty()) {
            restriction.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        }

        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String query = "INSERT INTO website_restrictions (device_id, domain_name, category, action, status) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, restriction.getDeviceId());
                pstmt.setString(2, restriction.getDomainName());
                pstmt.setString(3, restriction.getCategory());
                pstmt.setString(4, restriction.getAction());
                pstmt.setString(5, restriction.getStatus());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        if (restriction.getRestrictionId() <= 0) {
            restriction.setRestrictionId(restrictionIdCounter.incrementAndGet());
        }

        // Avoid duplicate in memory
        inMemoryRestrictions.removeIf(r -> r.getDomainName().equalsIgnoreCase(restriction.getDomainName()));
        inMemoryRestrictions.add(0, restriction);
    }

    public void toggleRestrictionStatus(int restrictionId, String newStatus) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE website_restrictions SET status = ? WHERE restriction_id = ?")) {
                pstmt.setString(1, newStatus);
                pstmt.setInt(2, restrictionId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        for (WebsiteRestriction r : inMemoryRestrictions) {
            if (r.getRestrictionId() == restrictionId) {
                r.setStatus(newStatus);
                break;
            }
        }
    }

    public void setDomainStatus(String domainName, String newStatus) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE website_restrictions SET status = ? WHERE LOWER(domain_name) = LOWER(?)")) {
                pstmt.setString(1, newStatus);
                pstmt.setString(2, domainName);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        for (WebsiteRestriction r : inMemoryRestrictions) {
            if (r.getDomainName().equalsIgnoreCase(domainName)) {
                r.setStatus(newStatus);
            }
        }
    }

    public void removeRestriction(int restrictionId) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM website_restrictions WHERE restriction_id = ?")) {
                pstmt.setInt(1, restrictionId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        inMemoryRestrictions.removeIf(r -> r.getRestrictionId() == restrictionId);
    }

    public List<WebsiteRestriction> getAllRestrictions() {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            List<WebsiteRestriction> list = new ArrayList<>();
            String query = "SELECT * FROM website_restrictions ORDER BY restriction_id DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String status = "BLOCKED";
                    try { status = rs.getString("status"); } catch (Exception ignored) {}
                    if (status == null || status.isEmpty()) status = "BLOCKED";

                    String createdAt = "";
                    try { createdAt = rs.getString("created_at"); } catch (Exception ignored) {}

                    list.add(new WebsiteRestriction(
                        rs.getInt("restriction_id"),
                        rs.getInt("device_id"),
                        rs.getString("domain_name"),
                        rs.getString("category"),
                        rs.getString("action"),
                        status,
                        createdAt
                    ));
                }
                if (!list.isEmpty()) {
                    inMemoryRestrictions.clear();
                    inMemoryRestrictions.addAll(list);
                    return list;
                }
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        return new ArrayList<>(inMemoryRestrictions);
    }
}
