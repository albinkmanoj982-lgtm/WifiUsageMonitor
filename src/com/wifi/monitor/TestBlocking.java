package com.wifi.monitor;

public class TestBlocking {
    public static void main(String[] args) {
        System.out.println("=== Testing WiFi Usage Monitor Blocking Subsystems ===");

        DeviceDAO deviceDAO = new DeviceDAO();
        RestrictionDAO restrictionDAO = new RestrictionDAO();

        // 1. Test Device Blocking Logic
        System.out.println("\n[Test 1] Device Blocking Check:");
        deviceDAO.registerDiscoveredClient("192.168.137.45");
        Device d = null;
        for (Device dev : deviceDAO.getAllDevices()) {
            if ("192.168.137.45".equals(dev.getIpAddress())) {
                d = dev;
                break;
            }
        }
        if (d != null) {
            System.out.println("Discovered device: " + d.getDeviceName() + ", isBlocked initial: " + d.isBlocked());
            boolean blockedBefore = deviceDAO.isIpBlocked("192.168.137.45");
            System.out.println("isIpBlocked before toggle: " + blockedBefore);

            deviceDAO.toggleBlockDevice(d.getDeviceId(), true);
            boolean blockedAfter = deviceDAO.isIpBlocked("192.168.137.45");
            System.out.println("isIpBlocked after toggle: " + blockedAfter);

            if (blockedAfter) {
                System.out.println("✅ Device blocking logic PASSED!");
            } else {
                System.out.println("❌ Device blocking logic FAILED!");
            }

            // Unblock
            deviceDAO.toggleBlockDevice(d.getDeviceId(), false);
            System.out.println("isIpBlocked after unblock: " + deviceDAO.isIpBlocked("192.168.137.45"));
        } else {
            System.out.println("❌ Could not register device.");
        }

        // 2. Test Website Restriction Matching
        System.out.println("\n[Test 2] Website Domain Matching:");
        System.out.println("youtube.com blocked? " + restrictionDAO.isDomainBlocked("youtube.com"));
        System.out.println("www.youtube.com blocked? " + restrictionDAO.isDomainBlocked("www.youtube.com"));
        System.out.println("m.youtube.com blocked? " + restrictionDAO.isDomainBlocked("m.youtube.com"));
        System.out.println("googlevideo.com (CDN alias) blocked? " + restrictionDAO.isDomainBlocked("googlevideo.com"));
        System.out.println("rr1---sn-4g5edn6s.googlevideo.com blocked? " + restrictionDAO.isDomainBlocked("rr1---sn-4g5edn6s.googlevideo.com"));
        System.out.println("google.com blocked? " + restrictionDAO.isDomainBlocked("google.com") + " (Expected: false)");

        if (restrictionDAO.isDomainBlocked("youtube.com") && 
            restrictionDAO.isDomainBlocked("www.youtube.com") && 
            restrictionDAO.isDomainBlocked("googlevideo.com") && 
            !restrictionDAO.isDomainBlocked("google.com")) {
            System.out.println("✅ Website restriction matching PASSED!");
        } else {
            System.out.println("❌ Website restriction matching FAILED!");
        }

        // 3. Test Windows Firewall Manager Elevation Detection
        System.out.println("\n[Test 3] Administrator Elevation Detection:");
        boolean elevated = WindowsFirewallManager.isElevated();
        System.out.println("Is process running as Administrator? " + elevated);
        System.out.println("Firewall status: " + WindowsFirewallManager.getStatusMessage());
        System.out.println("Hosts status: " + WindowsFirewallManager.getHostsStatusMessage());

        // 4. Test DNS Filter Server Live UDP Query
        System.out.println("\n[Test 4] Live DNS Filter Sinkhole Query:");
        DnsFilterServer dnsServer = new DnsFilterServer();
        dnsServer.start();
        try {
            Thread.sleep(300); // Allow socket bind
            int port = dnsServer.getBoundPort();
            System.out.println("DNS Filter Server bound on port: " + port);

            // Construct DNS query packet for "youtube.com" (Type A)
            java.io.ByteArrayOutputStream queryStream = new java.io.ByteArrayOutputStream();
            // Header: ID=0x1234, Flags=0x0100 (standard query, RD=1), QDCOUNT=1, ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
            queryStream.write(new byte[]{0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
            // Question: 7 "youtube" 3 "com" 0
            queryStream.write(0x07);
            queryStream.write("youtube".getBytes());
            queryStream.write(0x03);
            queryStream.write("com".getBytes());
            queryStream.write(0x00);
            // QTYPE=1 (A), QCLASS=1 (IN)
            queryStream.write(new byte[]{0x00, 0x01, 0x00, 0x01});

            byte[] queryBytes = queryStream.toByteArray();
            java.net.DatagramSocket client = new java.net.DatagramSocket();
            client.setSoTimeout(3000);
            java.net.InetAddress dest = java.net.InetAddress.getByName("127.0.0.1");
            java.net.DatagramPacket sendPkt = new java.net.DatagramPacket(queryBytes, queryBytes.length, dest, port);
            client.send(sendPkt);

            byte[] respBuf = new byte[1024];
            java.net.DatagramPacket respPkt = new java.net.DatagramPacket(respBuf, respBuf.length);
            client.receive(respPkt);

            // Check if last 4 bytes of answer are 0.0.0.0
            int len = respPkt.getLength();
            byte[] respData = respPkt.getData();
            boolean isSinkhole = (len >= 4 && respData[len-4] == 0 && respData[len-3] == 0 && respData[len-2] == 0 && respData[len-1] == 0);
            System.out.println("DNS Response received: " + len + " bytes, ends in 0.0.0.0 (Sinkhole)? " + isSinkhole);

            if (isSinkhole) {
                System.out.println("✅ Live DNS sinkhole test PASSED!");
            } else {
                System.out.println("❌ Live DNS sinkhole test FAILED!");
            }
            client.close();
        } catch (Exception e) {
            System.out.println("Notice testing live DNS query: " + e.getMessage());
        } finally {
            dnsServer.stop();
        }

        System.out.println("\n=== All Unit Checks Completed ===");
    }
}
