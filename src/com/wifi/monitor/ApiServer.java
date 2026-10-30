package com.wifi.monitor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ApiServer {

    private static final int PORT = 8080;
    private HttpServer server;

    public static final DnsFilterServer dnsFilterServer = new DnsFilterServer();
    public static final HttpFilterProxy httpFilterProxy = new HttpFilterProxy();

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API Context Routes
        server.createContext("/api/login",           new ApiHandler("/api/login"));
        server.createContext("/api/register",        new ApiHandler("/api/register"));
        server.createContext("/api/dashboard",       new ApiHandler("/api/dashboard"));
        server.createContext("/api/devices",         new ApiHandler("/api/devices"));
        server.createContext("/api/usage",           new ApiHandler("/api/usage"));
        server.createContext("/api/history",         new ApiHandler("/api/history"));
        server.createContext("/api/report",          new ApiHandler("/api/report"));
        server.createContext("/api/reports/export",  new ApiHandler("/api/reports/export"));
        server.createContext("/api/alerts",          new ApiHandler("/api/alerts"));
        server.createContext("/api/restrictions",    new ApiHandler("/api/restrictions"));
        server.createContext("/api/blocking/events", new ApiHandler("/api/blocking/events"));
        server.createContext("/api/network/info",    new ApiHandler("/api/network/info"));
        server.createContext("/api/ai/anomalies",    new ApiHandler("/api/ai/anomalies"));
        server.createContext("/api/database/status", new ApiHandler("/api/database/status"));
        server.createContext("/api/admin/elevate",   new ApiHandler("/api/admin/elevate"));

        // Static Web Files from /web folder
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("✅ REST API service started on port " + PORT);

        // Start Network Filtering Engines
        try {
            dnsFilterServer.start();
        } catch (Exception e) {
            System.out.println("Notice starting DNS Filter: " + e.getMessage());
        }

        try {
            httpFilterProxy.start();
        } catch (Exception e) {
            System.out.println("Notice starting Proxy Filter: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("API service stopped.");
        }
        try { dnsFilterServer.stop(); } catch (Exception ignored) {}
        try { httpFilterProxy.stop(); } catch (Exception ignored) {}
    }
}
