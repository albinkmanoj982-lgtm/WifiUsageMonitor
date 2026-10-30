package com.wifi.monitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;

public class MainDashboard extends JFrame {

    private final String currentUsername;
    private final DeviceDAO deviceDAO = new DeviceDAO();
    private final UsageDAO usageDAO = new UsageDAO();
    private final RestrictionDAO restrictionDAO = new RestrictionDAO();
    private final BlockedLogDAO blockedLogDAO = new BlockedLogDAO();
    private final ReportGenerator reportGenerator = new ReportGenerator();
    private final AiAnomalyDetector aiDetector = new AiAnomalyDetector();

    // Design Tokens & Dark Theme Colors
    public static final Color BG_APP = new Color(14, 18, 23);         // #0e1217
    public static final Color BG_CARD = new Color(21, 26, 33);        // #151a21
    public static final Color BG_CARD_HOVER = new Color(26, 32, 41);  // #1a2029
    public static final Color BG_CARD_SUBTLE = new Color(18, 22, 29); // #12161d
    public static final Color BG_SIDEBAR = new Color(17, 21, 28);     // #11151c
    public static final Color BG_INPUT = new Color(11, 14, 19);       // #0b0e13
    public static final Color BORDER_COLOR = new Color(43, 51, 64);   // #2b3340
    public static final Color BORDER_SUBTLE = new Color(30, 37, 48);  // #1e2530

    public static final Color ACCENT_BLUE = new Color(56, 139, 253);       // #388bfd
    public static final Color ACCENT_BLUE_HOVER = new Color(43, 116, 217); // #2b74d9
    public static final Color ACCENT_GREEN = new Color(35, 134, 54);       // #238636
    public static final Color ACCENT_GREEN_LIGHT = new Color(46, 160, 67); // #2ea043
    public static final Color ACCENT_RED = new Color(207, 34, 46);         // #cf222e
    public static final Color ACCENT_RED_LIGHT = new Color(229, 83, 75);   // #e5534b
    public static final Color ACCENT_AMBER = new Color(210, 153, 34);      // #d29922
    public static final Color ACCENT_PURPLE = new Color(137, 87, 229);     // #8957e5
    public static final Color ACCENT_CYAN = new Color(56, 189, 248);       // #38bdf8

    public static final Color TEXT_PRIMARY = new Color(230, 237, 243);     // #e6edf3
    public static final Color TEXT_SECONDARY = new Color(139, 148, 158);   // #8b949e
    public static final Color TEXT_MUTED = new Color(110, 118, 129);       // #6e7681

    // Navigation & View Switching
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, JButton> navButtons = new HashMap<>();
    private String activeTab = "dashboard";

    // Dynamic Top App Bar Components
    private JLabel lblTopPingVal;
    private JLabel lblTopUsername;

    // Sidebar Notification Badge
    private JLabel lblAiSidebarBadge;

    // Tab 1: Dashboard Overview Components
    private JLabel lblStatTotalVal;
    private JLabel lblStatSplitVal;
    private JLabel lblStatDevicesVal;
    private JLabel lblStatDevicesSub;
    private JLabel lblStatTodayVal;
    private DoughnutChartPanel doughnutChart;
    private SpeedChartPanel speedChart;
    private JLabel lblDashDl;
    private JLabel lblDashUl;
    private JLabel lblDashPing;

    // Tab 2: Usage History & Reports Components
    private HistoryBarChartPanel historyChart;
    private JTextArea txtReportTerminal;
    private JTextField txtAuditDate;
    private JTextField txtFromDate;
    private JTextField txtToDate;
    private JComboBox<String> cmbReportPeriod;
    private JPanel dateCardsPanel;
    private CardLayout dateCardsLayout;
    private JButton btnTabDaily;
    private JButton btnTabWeekly;
    private JButton btnTabMonthly;
    private String currentHistoryRange = "daily";

    // Tab 2: Summary Cards & Live Status
    private JLabel lblHistTotalUsage;
    private JLabel lblHistTotalSub;
    private JLabel lblHistDownload;
    private JLabel lblHistDlSub;
    private JLabel lblHistUpload;
    private JLabel lblHistUlSub;
    private JLabel lblHistActiveDevices;
    private JLabel lblHistDevSub;
    private JLabel lblHistLastUpdated;

    // Tab 2: Device Usage Table with Limit Progress Bars
    private JTable tblHistDevices;
    private DefaultTableModel modelHistDevices;

    // Tab 2: Top Data Consumers & Network Summary Panels
    private TopConsumersPanel topConsumersPanel;
    private NetworkSummaryPanel networkSummaryPanel;

    // Tab 2: Report Metadata Chips
    private JLabel lblReportMetaDate;
    private JLabel lblReportMetaGen;
    private JLabel lblReportMetaPeriod;
    private JLabel lblReportMetaDev;

    // Tab 3: Device Registry Components
    private JTable tblDevices;
    private DefaultTableModel modelDevices;
    private List<Device> cachedDevices = new ArrayList<>();

    // Tab 4: Admin Controls Components
    private JComboBox<String> cmbAdminDevice;
    private JTextField txtAdminDataLimit;
    private JTextField txtAdminSpeedLimit;
    private JButton btnAdminBlockToggle;
    private JLabel lblAdminMsg;
    private JTable tblAdminUsers;
    private DefaultTableModel modelAdminUsers;
    private JLabel lblDbStatusDiag;
    private JTextArea lblNetworkTopologyDiag;

    // Tab 5: Traffic Anomaly Detection Components
    private JLabel lblRiskScoreVal;
    private JLabel lblRiskTitle;
    private JLabel lblRiskDesc;
    private JPanel riskBannerCard;
    private JLabel lblAiFindingsCount;
    private JPanel anomaliesContainer;
    private List<AiAnomalyDetector.AnomalyResult> currentAnomalies = new ArrayList<>();

    // Tab 6: Website Blocking & Firewall Components
    private JLabel lblDnsServerStatus;
    private JLabel lblProxyServerStatus;
    private JLabel lblHotspotIp;
    private JLabel lblBlockedRulesCount;
    private JTextField txtBlockDomainInput;
    private JComboBox<String> cmbBlockCategory;
    private JLabel lblBlockMsg;
    private JTable tblBlockedWebsites;
    private DefaultTableModel modelBlockedWebsites;
    private JPanel liveFeedContainer;
    private long previousLatestLogId = 0;

    // Sidebar Telemetry & Status Components
    private JLabel lblSidebarDnsVal;
    private JLabel lblSidebarProxyVal;
    private JLabel lblSidebarRulesVal;
    private JLabel lblSidebarPingVal;

    // Real-Time Bandwidth speed meter variables
    private long lastReceivedBytes = 0;
    private long lastSentBytes = 0;
    private long lastSpeedTimestamp = 0;
    private int hostDeviceId = -1;
    private double accumulatedGb = 0.0;
    private int loopCounter = 0;

    public MainDashboard(String username) {
        this.currentUsername = (username != null && !username.isEmpty()) ? username : "admin";

        setTitle("WiFi Usage Monitor — Network Dashboard");
        setSize(1260, 820);
        setMinimumSize(new Dimension(1080, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // App Root Container
        JPanel appRoot = new JPanel(new BorderLayout());
        appRoot.setBackground(BG_APP);

        // 1. Top App Bar
        appRoot.add(createTopAppBar(), BorderLayout.NORTH);

        // 2. Sidebar Navigation
        appRoot.add(createSidebar(), BorderLayout.WEST);

        // 3. Main Content View Area with 6 Tabs
        contentPanel.setOpaque(false);
        contentPanel.add(createDashboardTab(), "dashboard");
        contentPanel.add(createHistoryTab(), "history");
        contentPanel.add(createDevicesTab(), "devices");
        contentPanel.add(createAdminTab(), "admin");
        contentPanel.add(createBlockingTab(), "blocking");
        contentPanel.add(createAiTab(), "ai");

        appRoot.add(contentPanel, BorderLayout.CENTER);
        add(appRoot);

        // Auto-Register/Lookup host computer
        initHostDevice();

        // Initial Data Load
        refreshAllData();

        // Start Real-Time Bandwidth Speedmeter Worker (1-second tick)
        startSpeedmeter();

        // Real-Time Polling for Blocked Activity Events (1.5 seconds tick)
        new javax.swing.Timer(1500, e -> pollBlockedEvents()).start();

        // Background Data Refresh Timer (6 seconds tick)
        new javax.swing.Timer(6000, e -> refreshAllData()).start();

        setVisible(true);
    }

    // =========================================================================
    // 1. TOP APP BAR
    // =========================================================================
    private JPanel createTopAppBar() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(BORDER_COLOR);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        header.setBackground(BG_CARD);
        header.setPreferredSize(new Dimension(0, 56));
        header.setBorder(new EmptyBorder(0, 20, 0, 20));

        // Left: Logo + Status Indicator Pill
        JPanel leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 11));
        leftBox.setOpaque(false);

        // WiFi Icon & Title
        JLabel lblLogo = new JLabel("WiFi Usage Monitor");
        lblLogo.setIcon(new NavIcon("wifi", 20, ACCENT_BLUE));
        lblLogo.setIconTextGap(10);
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblLogo.setForeground(TEXT_PRIMARY);
        leftBox.add(lblLogo);


        // Right: Latency meter + User Chip + Logout
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        rightBox.setOpaque(false);

        // Latency chip
        JPanel pingChip = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 3)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD_SUBTLE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
            }
        };
        pingChip.setOpaque(false);
        pingChip.setPreferredSize(new Dimension(120, 28));

        JLabel lblPingLbl = new JLabel("Latency:");
        lblPingLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPingLbl.setForeground(TEXT_SECONDARY);

        lblTopPingVal = new JLabel("14 ms");
        lblTopPingVal.setFont(new Font("Consolas", Font.BOLD, 12));
        lblTopPingVal.setForeground(ACCENT_CYAN);

        pingChip.add(lblPingLbl);
        pingChip.add(lblTopPingVal);
        rightBox.add(pingChip);

        // User Chip Container
        JPanel userChip = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 3)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(26, 32, 41));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        userChip.setOpaque(false);
        userChip.setPreferredSize(new Dimension(210, 32));

        // Circular Avatar Letter
        JPanel avatarCircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT_BLUE);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                String initial = currentUsername.isEmpty() ? "A" : currentUsername.substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(initial)) / 2;
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initial, tx, ty);
                g2.dispose();
            }
        };
        avatarCircle.setOpaque(false);
        avatarCircle.setPreferredSize(new Dimension(22, 22));

        lblTopUsername = new JLabel(currentUsername);
        lblTopUsername.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTopUsername.setForeground(TEXT_PRIMARY);

        JButton btnLogout = createSmallButton("Log Out", ACCENT_RED);
        btnLogout.setPreferredSize(new Dimension(72, 24));
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnLogout.addActionListener(e -> logoutUser());

        userChip.add(avatarCircle);
        userChip.add(lblTopUsername);
        userChip.add(btnLogout);
        rightBox.add(userChip);

        header.add(leftBox, BorderLayout.WEST);
        header.add(rightBox, BorderLayout.EAST);
        return header;
    }

    private void logoutUser() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out?", "Logout Confirmation",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            this.dispose();
            SwingUtilities.invokeLater(LoginSignupUI::new);
        }
    }

    // =========================================================================
    // 2. SIDEBAR NAVIGATION
    // =========================================================================
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(BORDER_COLOR);
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(270, 0));

        // Navigation Buttons List
        JPanel navList = new JPanel();
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setOpaque(false);
        navList.setAlignmentX(Component.LEFT_ALIGNMENT);
        navList.setBorder(new EmptyBorder(12, 12, 6, 12));

        lblAiSidebarBadge = new JLabel("0");

        JButton btnDashboard = createNavButton("Dashboard", "dashboard", "dashboard");
        JButton btnHistory   = createNavButton("History & Reports", "history", "history");
        JButton btnDevices   = createNavButton("Device Registry", "devices", "devices");
        JButton btnAdmin     = createNavButton("Admin Controls", "admin", "admin");
        JButton btnBlocking  = createNavButton("Website Blocking", "blocking", "blocking");
        JButton btnAi        = createNavButton("Anomaly Detection", "ai", "ai");

        // Section 1: Monitoring
        navList.add(createNavSectionHeader("TRAFFIC & MONITORING"));
        navList.add(btnDashboard);
        navList.add(Box.createRigidArea(new Dimension(0, 4)));
        navList.add(btnHistory);
        navList.add(Box.createRigidArea(new Dimension(0, 4)));
        navList.add(btnDevices);
        navList.add(Box.createRigidArea(new Dimension(0, 8)));

        // Section 2: Security & Firewall
        navList.add(createNavSectionHeader("SECURITY & POLICIES"));
        navList.add(btnAdmin);
        navList.add(Box.createRigidArea(new Dimension(0, 4)));
        navList.add(btnBlocking);
        navList.add(Box.createRigidArea(new Dimension(0, 4)));
        navList.add(btnAi);

        sidebar.add(navList, BorderLayout.NORTH);

        // Center Area: Active Protection Telemetry Panel
        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.setBorder(new EmptyBorder(4, 12, 6, 12));
        centerWrapper.add(createSidebarProtectionCard(), BorderLayout.CENTER);
        sidebar.add(centerWrapper, BorderLayout.CENTER);

        // Sidebar Footer Info Box: Network Interface Card
        JPanel footerBox = new JPanel();
        footerBox.setLayout(new BoxLayout(footerBox, BoxLayout.Y_AXIS));
        footerBox.setOpaque(false);
        footerBox.setBorder(new EmptyBorder(4, 12, 14, 12));

        JPanel footerCard = createCardPanel(8);
        footerCard.setLayout(new BoxLayout(footerCard, BoxLayout.Y_AXIS));
        footerCard.setBorder(new EmptyBorder(10, 14, 10, 14));
        footerCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JLabel lblNetTitle = new JLabel("NETWORK INTERFACE");
        lblNetTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblNetTitle.setForeground(new Color(110, 126, 145));
        lblNetTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        String subnetStr = localIp.contains(".") ? localIp.substring(0, localIp.lastIndexOf('.')) + ".0/24" : "10.0.0.0/24";

        JLabel lblModeTitle = new JLabel("Host IP:  " + localIp);
        lblModeTitle.setFont(new Font("Consolas", Font.BOLD, 11));
        lblModeTitle.setForeground(ACCENT_GREEN_LIGHT);
        lblModeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSubnetTitle = new JLabel("Subnet:   " + subnetStr);
        lblSubnetTitle.setFont(new Font("Consolas", Font.PLAIN, 11));
        lblSubnetTitle.setForeground(TEXT_SECONDARY);
        lblSubnetTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblSidebarPingVal = new JLabel("Latency:  14 ms");
        lblSidebarPingVal.setFont(new Font("Consolas", Font.PLAIN, 11));
        lblSidebarPingVal.setForeground(ACCENT_CYAN);
        lblSidebarPingVal.setAlignmentX(Component.LEFT_ALIGNMENT);

        footerCard.add(lblNetTitle);
        footerCard.add(Box.createRigidArea(new Dimension(0, 6)));
        footerCard.add(lblModeTitle);
        footerCard.add(Box.createRigidArea(new Dimension(0, 3)));
        footerCard.add(lblSubnetTitle);
        footerCard.add(Box.createRigidArea(new Dimension(0, 3)));
        footerCard.add(lblSidebarPingVal);
        footerCard.add(Box.createRigidArea(new Dimension(0, 8)));

        // Quick Sync Network Action Button
        JButton btnQuickSync = new JButton("Sync Network Data") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(38, 48, 64) : new Color(26, 34, 46));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnQuickSync.setIcon(new NavIcon("sync", 13, ACCENT_BLUE));
        btnQuickSync.setIconTextGap(7);
        btnQuickSync.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btnQuickSync.setForeground(TEXT_PRIMARY);
        btnQuickSync.setOpaque(false);
        btnQuickSync.setContentAreaFilled(false);
        btnQuickSync.setFocusPainted(false);
        btnQuickSync.setBorderPainted(false);
        btnQuickSync.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnQuickSync.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnQuickSync.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        btnQuickSync.setPreferredSize(new Dimension(246, 26));
        btnQuickSync.addActionListener(e -> {
            new Thread(() -> {
                deviceDAO.syncNetworkDevicesFromArp();
                SwingUtilities.invokeLater(() -> {
                    refreshAllData();
                    JOptionPane.showMessageDialog(this,
                            "Live network interface, Hotspot clients, and ARP cache synchronized with XAMPP MySQL database.",
                            "Network & Database Synchronized", JOptionPane.INFORMATION_MESSAGE);
                });
            }).start();
        });
        footerCard.add(btnQuickSync);
        footerBox.add(footerCard);

        sidebar.add(footerBox, BorderLayout.SOUTH);

        // Activate default tab
        setActiveNav(btnDashboard);
        return sidebar;
    }

    private JLabel createNavSectionHeader(String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(new Color(110, 126, 145));
        lbl.setBorder(new EmptyBorder(8, 8, 4, 8));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JPanel createSidebarProtectionCard() {
        JPanel card = createCardPanel(8);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        card.setPreferredSize(new Dimension(246, 175));

        // Header Row
        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        headerRow.setPreferredSize(new Dimension(218, 18));

        JLabel lblTitle = new JLabel("ACTIVE PROTECTION");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(new Color(110, 126, 145));

        JLabel lblOnline = new JLabel("● LIVE");
        lblOnline.setFont(new Font("Segoe UI", Font.BOLD, 9));
        lblOnline.setForeground(ACCENT_GREEN_LIGHT);

        headerRow.add(lblTitle, BorderLayout.WEST);
        headerRow.add(lblOnline, BorderLayout.EAST);
        card.add(headerRow);
        card.add(Box.createRigidArea(new Dimension(0, 8)));

        // Separator line
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_COLOR);
        sep.setBackground(BORDER_COLOR);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        card.add(sep);
        card.add(Box.createRigidArea(new Dimension(0, 8)));

        // Row 1: DNS Sinkhole
        boolean dnsOn = Main.getDnsFilterServer() != null && Main.getDnsFilterServer().isRunning();
        lblSidebarDnsVal = new JLabel(dnsOn ? "UDP:53" : "Off");
        card.add(createServiceRow("DNS Sinkhole", lblSidebarDnsVal, dnsOn ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT));
        card.add(Box.createRigidArea(new Dimension(0, 5)));

        // Row 2: HTTP Proxy Guard
        boolean proxyOn = Main.getHttpFilterProxy() != null && Main.getHttpFilterProxy().isRunning();
        lblSidebarProxyVal = new JLabel(proxyOn ? "TCP:8081" : "Off");
        card.add(createServiceRow("HTTP Proxy Guard", lblSidebarProxyVal, proxyOn ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT));
        card.add(Box.createRigidArea(new Dimension(0, 5)));

        // Row 3: Active Filter Rules
        lblSidebarRulesVal = new JLabel("0 Rules");
        card.add(createServiceRow("Firewall Filter", lblSidebarRulesVal, ACCENT_CYAN));
        card.add(Box.createRigidArea(new Dimension(0, 5)));

        // Row 4: ARP Live Discovery
        JLabel lblArpVal = new JLabel("Active");
        card.add(createServiceRow("ARP Sniffer", lblArpVal, ACCENT_GREEN_LIGHT));
        card.add(Box.createRigidArea(new Dimension(0, 5)));

        // Row 5: Real-Time Netstat
        JLabel lblNetstatVal = new JLabel("1s Sync");
        card.add(createServiceRow("Bandwidth Poller", lblNetstatVal, ACCENT_PURPLE));

        return card;
    }

    private JPanel createServiceRow(String label, JLabel valLbl, Color color) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.setPreferredSize(new Dimension(218, 20));

        JLabel lbl = new JLabel("● " + label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(TEXT_SECONDARY);
        lbl.setHorizontalAlignment(SwingConstants.LEFT);

        valLbl.setFont(new Font("Consolas", Font.BOLD, 10));
        valLbl.setForeground(color);
        valLbl.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(lbl, BorderLayout.CENTER);
        row.add(valLbl, BorderLayout.EAST);
        return row;
    }

    private JButton createNavButton(String title, String cardKey, String iconType) {
        JButton btn = new JButton(title) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (cardKey.equals(activeTab)) {
                    g2.setColor(new Color(28, 36, 51));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(ACCENT_BLUE);
                    g2.fillRoundRect(0, 4, 3, getHeight() - 8, 2, 2);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(22, 29, 39));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);

                // For the AI tab, draw the notification badge neatly on the far right
                if ("ai".equals(cardKey) && lblAiSidebarBadge != null) {
                    Graphics2D bg2 = (Graphics2D) g.create();
                    bg2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    String val = lblAiSidebarBadge.getText();
                    if (val == null || val.isEmpty()) val = "0";
                    int bw = 24;
                    int bh = 18;
                    int bx = getWidth() - bw - 12;
                    int by = (getHeight() - bh) / 2;
                    bg2.setColor(val.equals("0") ? new Color(35, 134, 54, 180) : new Color(207, 34, 46, 200));
                    bg2.fillRoundRect(bx, by, bw, bh, 8, 8);
                    bg2.setColor(Color.WHITE);
                    bg2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    FontMetrics fm = bg2.getFontMetrics();
                    int tx = bx + (bw - fm.stringWidth(val)) / 2;
                    int ty = by + (bh - fm.getHeight()) / 2 + fm.getAscent();
                    bg2.drawString(val, tx, ty);
                    bg2.dispose();
                }
            }
        };
        if (iconType != null) {
            btn.setIcon(new NavIcon(iconType));
            btn.setIconTextGap(10);
        }
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(TEXT_SECONDARY);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btn.setPreferredSize(new Dimension(246, 42));
        btn.setBorder(new EmptyBorder(0, 14, 0, 14));

        btn.addActionListener(e -> navigateTo(cardKey, btn));
        navButtons.put(cardKey, btn);
        return btn;
    }

    public void navigateTo(String cardKey, JButton sourceBtn) {
        activeTab = cardKey;
        cardLayout.show(contentPanel, cardKey);
        setActiveNav(sourceBtn != null ? sourceBtn : navButtons.get(cardKey));

        if ("history".equals(cardKey)) {
            refreshHistoryChart();
        } else if ("ai".equals(cardKey)) {
            runAiScan();
        }
    }

    private void setActiveNav(JButton activeBtn) {
        for (JButton b : navButtons.values()) {
            b.setForeground(TEXT_SECONDARY);
            b.repaint();
        }
        if (activeBtn != null) {
            activeBtn.setForeground(TEXT_PRIMARY);
            activeBtn.repaint();
        }
    }

    // =========================================================================
    // TAB 1: DASHBOARD OVERVIEW
    // =========================================================================
    private JPanel createDashboardTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 16));
        tab.setOpaque(false);
        tab.setBorder(new EmptyBorder(22, 26, 22, 26));

        // Header Title
        tab.add(createSectionHeader("Network Dashboard", "Overview of cumulative bandwidth, active client counts, and usage distribution", null), BorderLayout.NORTH);

        // Center Content: Stat Cards + Visual Split Grid
        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);

        // 4 Stat Cards in 1x4 Grid
        JPanel statCardsGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        statCardsGrid.setOpaque(false);
        statCardsGrid.setPreferredSize(new Dimension(0, 102));

        statCardsGrid.add(createMetricCard("Total Data Used", "Traffic", ACCENT_BLUE, lblStatTotalVal = new JLabel("0.00 GB"), "Lifetime data logged"));
        statCardsGrid.add(createMetricCard("Download / Upload", "Rx / Tx", ACCENT_GREEN_LIGHT, lblStatSplitVal = new JLabel("↓ 0.00 / ↑ 0.00 GB"), "Rx vs Tx breakdown", 15));
        statCardsGrid.add(createMetricCard("Connected Devices", "Clients", ACCENT_CYAN, lblStatDevicesVal = new JLabel("0 Devices"), lblStatDevicesSub = new JLabel("0 total registered")));
        statCardsGrid.add(createMetricCard("Today's Usage", "24 Hours", ACCENT_PURPLE, lblStatTodayVal = new JLabel("0.00 GB"), "Usage logged since 00:00"));

        center.add(statCardsGrid, BorderLayout.NORTH);

        // Split Grid (Doughnut Chart on Left, Real-Time Bandwidth on Right)
        JPanel splitGrid = new JPanel(new GridLayout(1, 2, 16, 0));
        splitGrid.setOpaque(false);

        // Panel 1: Usage by Device Distribution (Doughnut)
        JPanel doughnutCard = createCardPanel(10);
        doughnutCard.setLayout(new BorderLayout(0, 10));
        doughnutCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel doughnutHeader = new JPanel(new BorderLayout());
        doughnutHeader.setOpaque(false);
        JLabel lblDoughnutTitle = new JLabel("Usage by Device Distribution");
        lblDoughnutTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblDoughnutTitle.setForeground(TEXT_PRIMARY);

        JLabel lblTagShare = createPillBadge("Network Share", ACCENT_BLUE);
        doughnutHeader.add(lblDoughnutTitle, BorderLayout.WEST);
        doughnutHeader.add(lblTagShare, BorderLayout.EAST);
        doughnutCard.add(doughnutHeader, BorderLayout.NORTH);

        doughnutChart = new DoughnutChartPanel();
        doughnutCard.add(doughnutChart, BorderLayout.CENTER);
        splitGrid.add(doughnutCard);

        // Panel 2: Real-Time Bandwidth Chart
        JPanel speedCard = createCardPanel(10);
        speedCard.setLayout(new BorderLayout(0, 10));
        speedCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel speedHeader = new JPanel(new BorderLayout());
        speedHeader.setOpaque(false);
        JLabel lblSpeedTitle = new JLabel("Real-Time Bandwidth");
        lblSpeedTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblSpeedTitle.setForeground(TEXT_PRIMARY);

        lblDashPing = new JLabel("14 ms");
        lblDashPing.setFont(new Font("Consolas", Font.BOLD, 11));
        lblDashPing.setForeground(ACCENT_CYAN);
        speedHeader.add(lblSpeedTitle, BorderLayout.WEST);
        speedHeader.add(lblDashPing, BorderLayout.EAST);

        // Speed Pills Row
        JPanel pillsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        pillsRow.setOpaque(false);

        JPanel pillDl = createSpeedPill("Download", lblDashDl = new JLabel("0.00 Mbps"), ACCENT_GREEN_LIGHT);
        JPanel pillUl = createSpeedPill("Upload", lblDashUl = new JLabel("0.00 Mbps"), ACCENT_BLUE);
        pillsRow.add(pillDl);
        pillsRow.add(pillUl);

        JPanel speedTopContainer = new JPanel(new BorderLayout(0, 8));
        speedTopContainer.setOpaque(false);
        speedTopContainer.add(speedHeader, BorderLayout.NORTH);
        speedTopContainer.add(pillsRow, BorderLayout.SOUTH);
        speedCard.add(speedTopContainer, BorderLayout.NORTH);

        speedChart = new SpeedChartPanel();
        speedCard.add(speedChart, BorderLayout.CENTER);
        splitGrid.add(speedCard);

        center.add(splitGrid, BorderLayout.CENTER);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel createMetricCard(String title, String badgeText, Color badgeColor, JLabel valueLabel, Object subtitleObj) {
        return createMetricCard(title, badgeText, badgeColor, valueLabel, subtitleObj, 22);
    }

    private JPanel createMetricCard(String title, String badgeText, Color badgeColor, JLabel valueLabel, Object subtitleObj, int valueFontSize) {
        JPanel card = createCardPanel(10);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setMaximumSize(new Dimension(3000, 20));
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblT = new JLabel(title);
        lblT.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblT.setForeground(TEXT_SECONDARY);

        JLabel lblBadge = createPillBadge(badgeText, badgeColor);
        topRow.add(lblT, BorderLayout.WEST);
        topRow.add(lblBadge, BorderLayout.EAST);

        if (valueFontSize <= 16) {
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, valueFontSize));
        } else {
            valueLabel.setFont(new Font("Consolas", Font.BOLD, valueFontSize));
        }
        valueLabel.setForeground(TEXT_PRIMARY);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Component r1 = Box.createRigidArea(new Dimension(0, 8));
        if (r1 instanceof JComponent) ((JComponent) r1).setAlignmentX(Component.LEFT_ALIGNMENT);
        Component r2 = Box.createRigidArea(new Dimension(0, 4));
        if (r2 instanceof JComponent) ((JComponent) r2).setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(topRow);
        card.add(r1);
        card.add(valueLabel);
        card.add(r2);

        if (subtitleObj instanceof JLabel) {
            JLabel sl = (JLabel) subtitleObj;
            sl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            sl.setForeground(TEXT_MUTED);
            sl.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(sl);
        } else {
            JLabel sl = new JLabel(String.valueOf(subtitleObj));
            sl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            sl.setForeground(TEXT_MUTED);
            sl.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(sl);
        }

        return card;
    }

    private JPanel createSpeedPill(String label, JLabel valLbl, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(2, 8, 2, 10));
        p.setPreferredSize(new Dimension(165, 26));
        JLabel l = new JLabel(label + ":");
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(TEXT_SECONDARY);

        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        valLbl.setForeground(color);

        p.add(l);
        p.add(valLbl);
        return p;
    }

    private JPanel createHistoryTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 14));
        tab.setOpaque(false);
        tab.setBorder(new EmptyBorder(18, 22, 18, 22));

        // 1. Top Actions: Status Pill + Timestamp + Export CSV + Print Report
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        JLabel statusPill = createPillBadge("● Monitoring Active", ACCENT_GREEN_LIGHT);
        lblHistLastUpdated = new JLabel("Last updated: Just now");
        lblHistLastUpdated.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHistLastUpdated.setForeground(TEXT_MUTED);

        JButton btnExport = createModernButton("Export CSV", new Color(33, 38, 45), TEXT_PRIMARY);
        btnExport.addActionListener(e -> exportHistoryCsv());

        JButton btnExportPdf = createModernButton("Export PDF", ACCENT_GREEN_LIGHT, Color.WHITE);
        btnExportPdf.addActionListener(e -> exportReportPdf());

        JButton btnPrint = createModernButton("Print Report", ACCENT_BLUE, Color.WHITE);
        btnPrint.addActionListener(e -> printCurrentReport());

        actions.add(statusPill);
        actions.add(lblHistLastUpdated);
        actions.add(btnExport);
        actions.add(btnExportPdf);
        actions.add(btnPrint);

        tab.add(createSectionHeader("Usage History & Reports", "Historical traffic statistics, trend graphs, and audit report generation", actions), BorderLayout.NORTH);

        // Center Container: Vertical Stack inside a responsive ScrollPane
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // 2. Summary Cards Grid (1x4)
        JPanel summaryGrid = new JPanel(new GridLayout(1, 4, 10, 0));
        summaryGrid.setOpaque(false);
        summaryGrid.setPreferredSize(new Dimension(930, 94));
        summaryGrid.setMaximumSize(new Dimension(3000, 94));
        summaryGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        summaryGrid.add(createMetricCard("Total Usage", "Traffic", ACCENT_BLUE,
                lblHistTotalUsage = new JLabel("0.00 GB"),
                lblHistTotalSub = new JLabel("Aggregate network traffic")));

        summaryGrid.add(createMetricCard("Download", "Rx", ACCENT_GREEN_LIGHT,
                lblHistDownload = new JLabel("0.00 GB"),
                lblHistDlSub = new JLabel("0% of overall traffic")));

        summaryGrid.add(createMetricCard("Upload", "Tx", ACCENT_CYAN,
                lblHistUpload = new JLabel("0.00 GB"),
                lblHistUlSub = new JLabel("0% of overall traffic")));

        summaryGrid.add(createMetricCard("Active Devices", "Clients", ACCENT_PURPLE,
                lblHistActiveDevices = new JLabel("0"),
                lblHistDevSub = new JLabel("0 total registered")));

        center.add(summaryGrid);
        center.add(Box.createRigidArea(new Dimension(0, 14)));

        // 3. Card 1: Controls + History Bar Chart
        JPanel chartCard = createCardPanel(10);
        chartCard.setLayout(new BorderLayout(0, 10));
        chartCard.setBorder(new EmptyBorder(14, 16, 14, 16));
        chartCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartCard.setMaximumSize(new Dimension(3000, 310));
        chartCard.setPreferredSize(new Dimension(940, 300));

        // Top Controls Bar: Segmented Buttons + Legend + Date Range Selector
        JPanel controlsBar = new JPanel(new BorderLayout(8, 0));
        controlsBar.setOpaque(false);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftControls.setOpaque(false);

        btnTabDaily = createSegmentButton("14-Day Daily", true);
        btnTabWeekly = createSegmentButton("Weekly", false);
        btnTabMonthly = createSegmentButton("Monthly", false);

        btnTabDaily.setPreferredSize(new Dimension(106, 28));
        btnTabWeekly.setPreferredSize(new Dimension(86, 28));
        btnTabMonthly.setPreferredSize(new Dimension(86, 28));

        btnTabDaily.addActionListener(e -> switchHistoryRange("daily"));
        btnTabWeekly.addActionListener(e -> switchHistoryRange("weekly"));
        btnTabMonthly.addActionListener(e -> switchHistoryRange("monthly"));

        leftControls.add(btnTabDaily);
        leftControls.add(btnTabWeekly);
        leftControls.add(btnTabMonthly);

        controlsBar.add(leftControls, BorderLayout.WEST);

        // Right Controls: Report Period & Date Picker
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightControls.setOpaque(false);

        JLabel lblPeriod = new JLabel("Period:");
        lblPeriod.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPeriod.setForeground(TEXT_SECONDARY);

        String[] periods = {"Daily", "14 Days", "Weekly", "Monthly", "Custom Range"};
        cmbReportPeriod = new JComboBox<>(periods);
        styleModernComboBox(cmbReportPeriod);
        cmbReportPeriod.setPreferredSize(new Dimension(105, 28));

        dateCardsLayout = new CardLayout();
        dateCardsPanel = new JPanel(dateCardsLayout);
        dateCardsPanel.setOpaque(false);

        // Single Date card
        JPanel pnlSingle = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pnlSingle.setOpaque(false);
        JLabel lblAud = new JLabel("Date:");
        lblAud.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblAud.setForeground(TEXT_SECONDARY);
        txtAuditDate = createModernTextField("YYYY-MM-DD");
        txtAuditDate.setText(LocalDate.now().toString());
        txtAuditDate.setPreferredSize(new Dimension(88, 28));
        pnlSingle.add(lblAud);
        pnlSingle.add(txtAuditDate);

        // Range Date card
        JPanel pnlRange = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pnlRange.setOpaque(false);
        JLabel lblFrom = new JLabel("From:");
        lblFrom.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFrom.setForeground(TEXT_SECONDARY);
        txtFromDate = createModernTextField("YYYY-MM-DD");
        txtFromDate.setText(LocalDate.now().minusDays(14).toString());
        txtFromDate.setPreferredSize(new Dimension(84, 28));

        JLabel lblTo = new JLabel("To:");
        lblTo.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTo.setForeground(TEXT_SECONDARY);
        txtToDate = createModernTextField("YYYY-MM-DD");
        txtToDate.setText(LocalDate.now().toString());
        txtToDate.setPreferredSize(new Dimension(84, 28));

        pnlRange.add(lblFrom);
        pnlRange.add(txtFromDate);
        pnlRange.add(lblTo);
        pnlRange.add(txtToDate);

        dateCardsPanel.add(pnlSingle, "single");
        dateCardsPanel.add(pnlRange, "range");

        cmbReportPeriod.addActionListener(e -> {
            String sel = (String) cmbReportPeriod.getSelectedItem();
            if ("Custom Range".equals(sel)) {
                dateCardsLayout.show(dateCardsPanel, "range");
            } else {
                dateCardsLayout.show(dateCardsPanel, "single");
            }
        });

        JButton btnFetchReport = createSmallButton("Fetch", ACCENT_BLUE);
        btnFetchReport.setToolTipText("Fetch audit report for selected period");
        btnFetchReport.setPreferredSize(new Dimension(68, 28));
        btnFetchReport.addActionListener(e -> generateDailyTextReport());

        rightControls.add(lblPeriod);
        rightControls.add(cmbReportPeriod);
        rightControls.add(dateCardsPanel);
        rightControls.add(btnFetchReport);

        controlsBar.add(rightControls, BorderLayout.EAST);
        chartCard.add(controlsBar, BorderLayout.NORTH);

        historyChart = new HistoryBarChartPanel();
        historyChart.setPreferredSize(new Dimension(0, 230));
        chartCard.add(historyChart, BorderLayout.CENTER);

        center.add(chartCard);
        center.add(Box.createRigidArea(new Dimension(0, 14)));

        // 4. Split Grid: Device Table with Limit Bars (Left) + Top Consumers & Network Summary (Right)
        JPanel splitGrid = new JPanel(new BorderLayout(14, 0));
        splitGrid.setOpaque(false);
        splitGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        splitGrid.setPreferredSize(new Dimension(940, 310));
        splitGrid.setMaximumSize(new Dimension(3000, 320));

        // LEFT CARD: Device Usage & Limit Allocation Table
        JPanel historyDevicesCard = createCardPanel(10);
        historyDevicesCard.setLayout(new BorderLayout(0, 8));
        historyDevicesCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        JLabel lblDevTitle = new JLabel("Device Usage & Limit Allocation");
        lblDevTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDevTitle.setForeground(TEXT_PRIMARY);

        JLabel lblDevSub = new JLabel("Real-time breakdown of upload, download, and configured quota consumption");
        lblDevSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblDevSub.setForeground(TEXT_SECONDARY);

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);
        titleStack.add(lblDevTitle);
        titleStack.add(Box.createRigidArea(new Dimension(0, 2)));
        titleStack.add(lblDevSub);
        tableHeader.add(titleStack, BorderLayout.WEST);

        historyDevicesCard.add(tableHeader, BorderLayout.NORTH);

        String[] histCols = {"Device", "Upload", "Download", "Total Usage", "Usage Limit & Progress", "Status"};
        modelHistDevices = new DefaultTableModel(histCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblHistDevices = new JTable(modelHistDevices);
        styleModernTable(tblHistDevices);
        tblHistDevices.setRowHeight(50);

        // Custom Cell Renderers for Device, Usage Limit Progress Bar, and Status
        tblHistDevices.getColumnModel().getColumn(0).setCellRenderer(new DeviceCellRenderer());
        tblHistDevices.getColumnModel().getColumn(0).setPreferredWidth(180);

        DefaultTableCellRenderer rightMonoCyan = new DefaultTableCellRenderer();
        rightMonoCyan.setHorizontalAlignment(SwingConstants.RIGHT);
        rightMonoCyan.setForeground(ACCENT_CYAN);
        rightMonoCyan.setFont(new Font("Consolas", Font.BOLD, 11));
        tblHistDevices.getColumnModel().getColumn(1).setCellRenderer(rightMonoCyan);
        tblHistDevices.getColumnModel().getColumn(1).setPreferredWidth(85);

        DefaultTableCellRenderer rightMonoGreen = new DefaultTableCellRenderer();
        rightMonoGreen.setHorizontalAlignment(SwingConstants.RIGHT);
        rightMonoGreen.setForeground(ACCENT_GREEN_LIGHT);
        rightMonoGreen.setFont(new Font("Consolas", Font.BOLD, 11));
        tblHistDevices.getColumnModel().getColumn(2).setCellRenderer(rightMonoGreen);
        tblHistDevices.getColumnModel().getColumn(2).setPreferredWidth(85);

        DefaultTableCellRenderer rightMonoTotal = new DefaultTableCellRenderer();
        rightMonoTotal.setHorizontalAlignment(SwingConstants.RIGHT);
        rightMonoTotal.setForeground(TEXT_PRIMARY);
        rightMonoTotal.setFont(new Font("Consolas", Font.BOLD, 11));
        tblHistDevices.getColumnModel().getColumn(3).setCellRenderer(rightMonoTotal);
        tblHistDevices.getColumnModel().getColumn(3).setPreferredWidth(90);

        tblHistDevices.getColumnModel().getColumn(4).setCellRenderer(new UsageLimitRenderer());
        tblHistDevices.getColumnModel().getColumn(4).setPreferredWidth(170);

        tblHistDevices.getColumnModel().getColumn(5).setCellRenderer(new StatusBadgeRenderer());
        tblHistDevices.getColumnModel().getColumn(5).setPreferredWidth(95);

        JScrollPane scrollHistDevices = new JScrollPane(tblHistDevices);
        scrollHistDevices.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollHistDevices.getViewport().setBackground(BG_CARD);
        historyDevicesCard.add(scrollHistDevices, BorderLayout.CENTER);

        splitGrid.add(historyDevicesCard, BorderLayout.CENTER);

        // RIGHT CARD: Top Data Consumers + Network Summary
        JPanel rightSideStack = new JPanel(new GridLayout(2, 1, 0, 10));
        rightSideStack.setOpaque(false);
        rightSideStack.setPreferredSize(new Dimension(300, 0));

        // Card A: Top Data Consumers
        JPanel topConsumersCard = createCardPanel(10);
        topConsumersCard.setLayout(new BorderLayout(0, 6));
        topConsumersCard.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel lblTopTitle = new JLabel("Top Data Consumers");
        lblTopTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTopTitle.setForeground(TEXT_PRIMARY);
        topConsumersCard.add(lblTopTitle, BorderLayout.NORTH);

        topConsumersPanel = new TopConsumersPanel();
        topConsumersCard.add(topConsumersPanel, BorderLayout.CENTER);
        rightSideStack.add(topConsumersCard);

        // Card B: Network Summary
        JPanel netSummaryCard = createCardPanel(10);
        netSummaryCard.setLayout(new BorderLayout(0, 6));
        netSummaryCard.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel lblNetSumTitle = new JLabel("Network Summary");
        lblNetSumTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblNetSumTitle.setForeground(TEXT_PRIMARY);
        netSummaryCard.add(lblNetSumTitle, BorderLayout.NORTH);

        networkSummaryPanel = new NetworkSummaryPanel();
        netSummaryCard.add(networkSummaryPanel, BorderLayout.CENTER);
        rightSideStack.add(netSummaryCard);

        splitGrid.add(rightSideStack, BorderLayout.EAST);

        center.add(splitGrid);
        center.add(Box.createRigidArea(new Dimension(0, 14)));

        // 5. Card 3: Generated Traffic Report Monospace Box
        JPanel reportCard = createCardPanel(10);
        reportCard.setLayout(new BorderLayout(0, 8));
        reportCard.setBorder(new EmptyBorder(14, 16, 14, 16));
        reportCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportCard.setMaximumSize(new Dimension(3000, 290));
        reportCard.setPreferredSize(new Dimension(1000, 270));

        JPanel repHeader = new JPanel(new BorderLayout());
        repHeader.setOpaque(false);

        JPanel repTitleStack = new JPanel();
        repTitleStack.setLayout(new BoxLayout(repTitleStack, BoxLayout.Y_AXIS));
        repTitleStack.setOpaque(false);

        JLabel lblRepTitle = new JLabel("Generated Traffic Report");
        lblRepTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblRepTitle.setForeground(TEXT_PRIMARY);

        JLabel lblRepSub = new JLabel("Comprehensive text audit report suitable for archiving, printing, and CSV export");
        lblRepSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblRepSub.setForeground(TEXT_SECONDARY);

        repTitleStack.add(lblRepTitle);
        repTitleStack.add(Box.createRigidArea(new Dimension(0, 2)));
        repTitleStack.add(lblRepSub);
        repHeader.add(repTitleStack, BorderLayout.WEST);

        JPanel repBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        repBtnRow.setOpaque(false);

        JButton btnCardExportPdf = createSmallButton("Export PDF", ACCENT_BLUE);
        btnCardExportPdf.addActionListener(e -> exportReportPdf());

        JButton btnCopy = createSmallButton("Copy Text", new Color(33, 38, 45));
        btnCopy.addActionListener(e -> copyReportToClipboard());

        repBtnRow.add(btnCardExportPdf);
        repBtnRow.add(btnCopy);

        repHeader.add(repBtnRow, BorderLayout.EAST);
        reportCard.add(repHeader, BorderLayout.NORTH);

        // Metadata Chips Row
        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        metaRow.setOpaque(false);

        lblReportMetaDate = createPillBadge("Report Date: " + LocalDate.now(), ACCENT_BLUE);
        lblReportMetaGen = createPillBadge("Generated: " + LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a")), ACCENT_GREEN_LIGHT);
        lblReportMetaPeriod = createPillBadge("Period: Single-Day Daily Audit", ACCENT_CYAN);
        lblReportMetaDev = createPillBadge("Devices: 0 Registered", ACCENT_PURPLE);

        metaRow.add(lblReportMetaDate);
        metaRow.add(lblReportMetaGen);
        metaRow.add(lblReportMetaPeriod);
        metaRow.add(lblReportMetaDev);

        JPanel repCenter = new JPanel(new BorderLayout(0, 6));
        repCenter.setOpaque(false);
        repCenter.add(metaRow, BorderLayout.NORTH);

        txtReportTerminal = new JTextArea("Select a period above and click 'Fetch Report' to generate audit report...");
        txtReportTerminal.setEditable(false);
        txtReportTerminal.setBackground(BG_INPUT);
        txtReportTerminal.setForeground(new Color(56, 189, 248));
        txtReportTerminal.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtReportTerminal.setMargin(new Insets(10, 12, 10, 12));

        JScrollPane scrollReport = new JScrollPane(txtReportTerminal);
        scrollReport.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollReport.getViewport().setBackground(BG_INPUT);
        repCenter.add(scrollReport, BorderLayout.CENTER);

        reportCard.add(repCenter, BorderLayout.CENTER);
        center.add(reportCard);

        // Responsive Outer ScrollPane
        JScrollPane mainScroll = new JScrollPane(center);
        mainScroll.setBorder(BorderFactory.createEmptyBorder());
        mainScroll.getViewport().setBackground(BG_APP);
        mainScroll.setOpaque(false);
        mainScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        mainScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Initial generation of report
        SwingUtilities.invokeLater(this::generateDailyTextReport);

        tab.add(mainScroll, BorderLayout.CENTER);
        return tab;
    }

    private void switchHistoryRange(String range) {
        currentHistoryRange = range;
        btnTabDaily.setBackground("daily".equals(range) ? ACCENT_BLUE : BG_CARD_SUBTLE);
        btnTabWeekly.setBackground("weekly".equals(range) ? ACCENT_BLUE : BG_CARD_SUBTLE);
        btnTabMonthly.setBackground("monthly".equals(range) ? ACCENT_BLUE : BG_CARD_SUBTLE);
        btnTabDaily.repaint();
        btnTabWeekly.repaint();
        btnTabMonthly.repaint();
        refreshHistoryChart();
    }

    private void refreshHistoryChart() {
        if (historyChart == null) return;
        List<Map<String, Object>> rawStats = usageDAO.getDailyStats(30);
        List<Map<String, Object>> displayData = new ArrayList<>();

        if ("weekly".equalsIgnoreCase(currentHistoryRange)) {
            // Aggregate records into Week 1 (past 7 days) and Week 2 (8-14 days ago)
            double w1Dl = 0, w1Ul = 0;
            double w2Dl = 0, w2Ul = 0;
            LocalDate today = LocalDate.now();
            for (Map<String, Object> r : rawStats) {
                String dStr = String.valueOf(r.getOrDefault("date", ""));
                try {
                    LocalDate ld = LocalDate.parse(dStr);
                    long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(ld, today);
                    double dl = ((Number) r.getOrDefault("download", 0.0)).doubleValue();
                    double ul = ((Number) r.getOrDefault("upload", 0.0)).doubleValue();
                    if (daysDiff >= 0 && daysDiff < 7) {
                        w1Dl += dl; w1Ul += ul;
                    } else if (daysDiff >= 7 && daysDiff < 14) {
                        w2Dl += dl; w2Ul += ul;
                    }
                } catch (Exception ignored) {}
            }
            Map<String, Object> m1 = new HashMap<>();
            m1.put("date", "Week 1 (Past 7d)");
            m1.put("download", w1Dl);
            m1.put("upload", w1Ul);
            displayData.add(m1);

            Map<String, Object> m2 = new HashMap<>();
            m2.put("date", "Week 2 (Prior 7d)");
            m2.put("download", w2Dl);
            m2.put("upload", w2Ul);
            displayData.add(m2);

        } else if ("monthly".equalsIgnoreCase(currentHistoryRange)) {
            // Group records by Month
            Map<String, double[]> monthMap = new LinkedHashMap<>();
            for (Map<String, Object> r : rawStats) {
                String dStr = String.valueOf(r.getOrDefault("date", ""));
                if (dStr.length() >= 7) {
                    String mKey = dStr.substring(0, 7);
                    double[] arr = monthMap.computeIfAbsent(mKey, k -> new double[2]);
                    arr[0] += ((Number) r.getOrDefault("download", 0.0)).doubleValue();
                    arr[1] += ((Number) r.getOrDefault("upload", 0.0)).doubleValue();
                }
            }
            if (monthMap.isEmpty()) {
                Map<String, Object> m = new HashMap<>();
                m.put("date", LocalDate.now().getMonth().toString().substring(0, 3));
                m.put("download", 0.0);
                m.put("upload", 0.0);
                displayData.add(m);
            } else {
                for (Map.Entry<String, double[]> e : monthMap.entrySet()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("date", e.getKey());
                    m.put("download", e.getValue()[0]);
                    m.put("upload", e.getValue()[1]);
                    displayData.add(m);
                }
            }
        } else {
            // 14-Day Daily: Past 14 consecutive days so no gaps occur
            LocalDate today = LocalDate.now();
            Map<String, Map<String, Object>> mapByDate = new HashMap<>();
            for (Map<String, Object> r : rawStats) {
                String d = String.valueOf(r.getOrDefault("date", ""));
                mapByDate.put(d, r);
            }

            for (int i = 13; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                String dayStr = day.toString();
                String shortLabel = day.getMonth().toString().substring(0, 3) + " " + day.getDayOfMonth();
                Map<String, Object> entry = new HashMap<>();
                entry.put("date", shortLabel);
                if (mapByDate.containsKey(dayStr)) {
                    Map<String, Object> exist = mapByDate.get(dayStr);
                    entry.put("download", ((Number) exist.getOrDefault("download", 0.0)).doubleValue());
                    entry.put("upload", ((Number) exist.getOrDefault("upload", 0.0)).doubleValue());
                } else {
                    entry.put("download", 0.0);
                    entry.put("upload", 0.0);
                }
                displayData.add(entry);
            }
        }

        historyChart.setData(displayData, currentHistoryRange);
    }

    private void refreshHistoryAnalytics() {
        if (lblHistTotalUsage == null) return;

        double totalDl = 0;
        double totalUl = 0;
        double totalNet = 0;
        int connectedCount = 0;

        for (Device d : cachedDevices) {
            double dl = d.getDownloadGb();
            double ul = d.getUploadGb();
            double tot = dl + ul;
            totalDl += dl;
            totalUl += ul;
            totalNet += tot;
            if ("Connected".equalsIgnoreCase(d.getStatus())) {
                connectedCount++;
            }
        }

        // 1. Update 4 Summary Cards
        lblHistTotalUsage.setText(String.format("%.2f GB", totalNet));
        lblHistDownload.setText(String.format("%.2f GB", totalDl));
        lblHistUpload.setText(String.format("%.2f GB", totalUl));
        lblHistActiveDevices.setText(String.valueOf(connectedCount));

        if (totalNet > 0) {
            lblHistDlSub.setText(String.format("%.0f%% of overall traffic", (totalDl / totalNet) * 100.0));
            lblHistUlSub.setText(String.format("%.0f%% of overall traffic", (totalUl / totalNet) * 100.0));
        } else {
            lblHistDlSub.setText("0% of overall traffic");
            lblHistUlSub.setText("0% of overall traffic");
        }
        lblHistDevSub.setText(cachedDevices.size() + " total registered");

        // 2. Update Status Timestamp
        if (lblHistLastUpdated != null) {
            lblHistLastUpdated.setText("Last updated: " + LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
        }

        // 3. Update Device Usage Table with Limits
        if (modelHistDevices != null) {
            modelHistDevices.setRowCount(0);
            for (Device d : cachedDevices) {
                double dl = d.getDownloadGb();
                double ul = d.getUploadGb();
                double tot = dl + ul;
                double limit = d.getDataLimit();
                boolean isExceeded = tot >= limit && limit > 0;
                boolean isNear = !isExceeded && limit > 0 && ((tot / limit) * 100.0 >= 80.0);

                String status = "Normal";
                if (d.isBlocked()) status = "Blocked";
                else if (isExceeded) status = "Limit Exceeded";
                else if (isNear) status = "Near Limit";

                modelHistDevices.addRow(new Object[]{
                        d,
                        String.format("↑ %.2f GB", ul),
                        String.format("↓ %.2f GB", dl),
                        String.format("%.2f GB", tot),
                        d,
                        status
                });
            }
        }

        // 4. Update Top Data Consumers
        if (topConsumersPanel != null) {
            topConsumersPanel.updateData(cachedDevices);
        }

        // 5. Update Network Summary
        if (networkSummaryPanel != null) {
            networkSummaryPanel.updateMetrics(totalDl, totalUl, totalNet, connectedCount, cachedDevices.size());
        }

        // 6. Refresh Chart
        refreshHistoryChart();
    }

    private void generateDailyTextReport() {
        String period = cmbReportPeriod != null ? (String) cmbReportPeriod.getSelectedItem() : "Daily";
        if (period == null) period = "Daily";

        String reportText = "";
        String dateDisplay = "";
        String periodLabel = "";
        LocalDate today = LocalDate.now();

        if (period.startsWith("Daily")) {
            String date = (txtAuditDate != null) ? txtAuditDate.getText().trim() : today.toString();
            if (date.isEmpty()) date = today.toString();
            dateDisplay = date;
            periodLabel = "Single-Day Daily Audit";
            reportText = usageDAO.getDailyReportText(date);
        } else if (period.startsWith("14 Days")) {
            String fromDate = today.minusDays(14).toString();
            String toDate = today.toString();
            dateDisplay = fromDate + " to " + toDate;
            periodLabel = "14-Day Rolling Audit";
            reportText = usageDAO.getRangeReportText(fromDate, toDate, periodLabel);
        } else if (period.startsWith("Weekly")) {
            String fromDate = today.minusDays(7).toString();
            String toDate = today.toString();
            dateDisplay = fromDate + " to " + toDate;
            periodLabel = "Weekly (Past 7 Days)";
            reportText = usageDAO.getRangeReportText(fromDate, toDate, periodLabel);
        } else if (period.startsWith("Monthly")) {
            String fromDate = today.minusDays(30).toString();
            String toDate = today.toString();
            dateDisplay = fromDate + " to " + toDate;
            periodLabel = "Monthly (Past 30 Days)";
            reportText = usageDAO.getRangeReportText(fromDate, toDate, periodLabel);
        } else if (period.startsWith("Custom")) {
            String fromDate = (txtFromDate != null) ? txtFromDate.getText().trim() : today.minusDays(14).toString();
            String toDate = (txtToDate != null) ? txtToDate.getText().trim() : today.toString();
            if (fromDate.isEmpty() || toDate.isEmpty()) {
                txtReportTerminal.setText("Please specify both From and To dates (YYYY-MM-DD).");
                return;
            }
            dateDisplay = fromDate + " to " + toDate;
            periodLabel = "Custom Date Range";
            reportText = usageDAO.getRangeReportText(fromDate, toDate, periodLabel);
        }

        txtReportTerminal.setText(reportText);
        txtReportTerminal.setCaretPosition(0);

        if (lblReportMetaDate != null) lblReportMetaDate.setText("Report Date: " + dateDisplay);
        if (lblReportMetaGen != null) lblReportMetaGen.setText("Generated: " + LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
        if (lblReportMetaPeriod != null) lblReportMetaPeriod.setText("Period: " + periodLabel);
        if (lblReportMetaDev != null) lblReportMetaDev.setText("Devices: " + cachedDevices.size() + " Registered");
    }

    private void copyReportToClipboard() {
        String text = txtReportTerminal.getText();
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No report content to copy.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            JOptionPane.showMessageDialog(this, "Audit report copied to clipboard successfully.", "Copied", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to copy to clipboard: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportReportPdf() {
        String text = (txtReportTerminal != null) ? txtReportTerminal.getText() : "";
        if (text == null || text.trim().isEmpty() || text.startsWith("Select a period")) {
            generateDailyTextReport();
            text = (txtReportTerminal != null) ? txtReportTerminal.getText() : "";
        }
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No report content available to export to PDF.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String period = cmbReportPeriod != null ? (String) cmbReportPeriod.getSelectedItem() : "Daily";
        String dateRange = (lblReportMetaDate != null) ? lblReportMetaDate.getText().replace("Report Date: ", "") : LocalDate.now().toString();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Audit Report as PDF");
        chooser.setSelectedFile(new File("wifi_audit_report_" + LocalDate.now() + ".pdf"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".pdf")) {
                target = new File(target.getParentFile(), target.getName() + ".pdf");
            }

            try {
                PdfReportExporter.exportReport(target, text, period, dateRange, currentUsername);
                int choice = JOptionPane.showOptionDialog(this,
                        "Audit report exported to PDF successfully!\n\nSaved to: " + target.getAbsolutePath(),
                        "PDF Export Successful",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        new Object[]{"Open PDF", "Close"},
                        "Open PDF");

                if (choice == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().open(target);
                    } catch (Exception ex) {
                        System.out.println("Notice opening PDF: " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export PDF: " + ex.getMessage(), "PDF Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportHistoryCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("wifi_traffic_report_" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(target)) {
                writer.write(reportGenerator.generateCsvReport());
                JOptionPane.showMessageDialog(this, "Exported successfully to:\n" + target.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to write CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void printCurrentReport() {
        try {
            boolean done = txtReportTerminal.print();
            if (done) {
                JOptionPane.showMessageDialog(this, "Report printed successfully.", "Print", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this, "Printer error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // TAB 3: DEVICE REGISTRY
    // =========================================================================
    private JPanel createDevicesTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 16));
        tab.setOpaque(false);
        tab.setBorder(new EmptyBorder(22, 26, 22, 26));

        // Right button: Register New Device
        JButton btnAddDevice = createModernButton("+ Register New Device", ACCENT_BLUE, Color.WHITE);
        btnAddDevice.addActionListener(e -> showAddDeviceModal());

        tab.add(createSectionHeader("Device Management Registry", "Registered client devices, IP submasks, hardware MAC addresses, and active connectivity", btnAddDevice), BorderLayout.NORTH);

        // Full-width Device Table Container
        JPanel tableCard = createCardPanel(10);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(0, 0, 0, 0));

        String[] cols = {"ID", "Device Name", "Owner", "IP Address", "MAC Address", "Status", "Upload / Download", "Data Cap", "Actions"};
        modelDevices = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblDevices = new JTable(modelDevices);
        styleModernTable(tblDevices);

        // Custom Column Alignments & Badges
        tblDevices.getColumnModel().getColumn(0).setPreferredWidth(50);
        tblDevices.getColumnModel().getColumn(1).setPreferredWidth(160);
        tblDevices.getColumnModel().getColumn(2).setPreferredWidth(120);
        tblDevices.getColumnModel().getColumn(3).setPreferredWidth(110);
        tblDevices.getColumnModel().getColumn(4).setPreferredWidth(140);
        tblDevices.getColumnModel().getColumn(5).setPreferredWidth(110);
        tblDevices.getColumnModel().getColumn(6).setPreferredWidth(160);
        tblDevices.getColumnModel().getColumn(7).setPreferredWidth(90);
        tblDevices.getColumnModel().getColumn(8).setPreferredWidth(140);

        // Actions Double Click / Context Menu
        tblDevices.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tblDevices.rowAtPoint(e.getPoint());
                int col = tblDevices.columnAtPoint(e.getPoint());
                if (row >= 0 && row < cachedDevices.size()) {
                    Device d = cachedDevices.get(row);
                    if (col == 5) {
                        // Toggle connected status
                        String newStatus = "Connected".equalsIgnoreCase(d.getStatus()) ? "Disconnected" : "Connected";
                        deviceDAO.updateStatus(d.getDeviceId(), newStatus);
                        refreshAllData();
                    } else if (col == 8) {
                        // Quick action toggle
                        showDeviceActionMenu(e.getComponent(), e.getX(), e.getY(), d);
                    }
                }
            }
        });

        JScrollPane scrollTable = new JScrollPane(tblDevices);
        scrollTable.setBorder(BorderFactory.createEmptyBorder());
        scrollTable.getViewport().setBackground(BG_CARD);

        tableCard.add(scrollTable, BorderLayout.CENTER);
        tab.add(tableCard, BorderLayout.CENTER);
        return tab;
    }

    private void showDeviceActionMenu(Component invoker, int x, int y, Device d) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_CARD);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JMenuItem itemBlock = new JMenuItem(d.isBlocked() ? "🟢 Unblock Device" : "🚫 Block Device");
        itemBlock.setForeground(d.isBlocked() ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT);
        itemBlock.setBackground(BG_CARD);
        itemBlock.addActionListener(e -> {
            deviceDAO.toggleBlockDevice(d.getDeviceId(), !d.isBlocked());
            refreshAllData();
        });

        JMenuItem itemEditLimit = new JMenuItem("✏️ Edit Data & Speed Limit");
        itemEditLimit.setForeground(TEXT_PRIMARY);
        itemEditLimit.setBackground(BG_CARD);
        itemEditLimit.addActionListener(e -> {
            String newLimit = JOptionPane.showInputDialog(this, "Enter new Data Limit (GB):", d.getDataLimit());
            if (newLimit != null && !newLimit.trim().isEmpty()) {
                try {
                    double dl = Double.parseDouble(newLimit.trim());
                    deviceDAO.setDeviceLimits(d.getDeviceId(), dl, d.getBandwidthLimitMbps());
                    refreshAllData();
                } catch (Exception ignored) {}
            }
        });

        JMenuItem itemDelete = new JMenuItem("🗑️ Delete Device");
        itemDelete.setForeground(ACCENT_RED_LIGHT);
        itemDelete.setBackground(BG_CARD);
        itemDelete.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Delete device '" + d.getDeviceName() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                deviceDAO.removeDevice(d.getDeviceId());
                refreshAllData();
            }
        });

        menu.add(itemBlock);
        menu.add(itemEditLimit);
        menu.addSeparator();
        menu.add(itemDelete);
        menu.show(invoker, x, y);
    }

    private void showAddDeviceModal() {
        JDialog dlg = new JDialog(this, "Register New Device", true);
        dlg.setSize(480, 520);
        dlg.setLocationRelativeTo(this);

        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(BG_CARD);
        p.setBorder(new EmptyBorder(22, 24, 22, 24));

        JLabel lblTitle = new JLabel("Register New Device");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(TEXT_PRIMARY);
        p.add(lblTitle, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);

        JButton btnAutoDetect = createModernButton("Auto-Detect Local Computer Hardware Info", new Color(33, 38, 45), ACCENT_CYAN);
        btnAutoDetect.setMaximumSize(new Dimension(430, 36));
        btnAutoDetect.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(btnAutoDetect);
        form.add(Box.createRigidArea(new Dimension(0, 14)));

        JTextField txtName = createFormInput(form, "Device Name", "e.g. Workstation PC");
        JTextField txtOwner = createFormInput(form, "Owner Name", "e.g. Alex Morgan");

        JLabel lblCat = new JLabel("Device Category");
        lblCat.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblCat.setForeground(TEXT_SECONDARY);
        lblCat.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lblCat);
        form.add(Box.createRigidArea(new Dimension(0, 4)));

        JComboBox<String> cmbType = new JComboBox<>(new String[]{"Laptop", "Desktop", "Mobile", "Smart TV", "IoT Device", "Other"});
        styleModernComboBox(cmbType);
        cmbType.setMaximumSize(new Dimension(430, 34));
        cmbType.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(cmbType);
        form.add(Box.createRigidArea(new Dimension(0, 10)));

        JTextField txtIp = createFormInput(form, "IP Address", "192.168.1.105");
        JTextField txtMac = createFormInput(form, "MAC Address", "AA:BB:CC:11:22:33");
        JTextField txtLimit = createFormInput(form, "Monthly Data Limit (GB)", "50");

        btnAutoDetect.addActionListener(e -> {
            try {
                String host = InetAddress.getLocalHost().getHostName();
                String ip = InetAddress.getLocalHost().getHostAddress();
                txtName.setText(cleanDeviceName(host, ip));
                txtOwner.setText(currentUsername);
                txtIp.setText(ip);
                txtMac.setText(generateRandomMac());
                cmbType.setSelectedItem(guessDeviceType(host));
            } catch (Exception ignored) {}
        });

        p.add(form, BorderLayout.CENTER);

        // Buttons bottom
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);

        JButton btnCancel = createModernButton("Cancel", new Color(33, 38, 45), TEXT_SECONDARY);
        btnCancel.addActionListener(e -> dlg.dispose());

        JButton btnSubmit = createModernButton("Register Device", ACCENT_BLUE, Color.WHITE);
        btnSubmit.addActionListener(e -> {
            String n = txtName.getText().trim();
            String o = txtOwner.getText().trim();
            String t = (String) cmbType.getSelectedItem();
            String ip = txtIp.getText().trim();
            String mac = txtMac.getText().trim();
            String limStr = txtLimit.getText().trim();

            if (n.isEmpty() || o.isEmpty() || limStr.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Please fill in Name, Owner, and Data Limit.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                double lim = Double.parseDouble(limStr);
                Device newDev = new Device(0, n, o, t, lim, ip.isEmpty() ? "192.168.1.100" : ip, mac.isEmpty() ? generateRandomMac() : mac, "Connected", false, 0.0, 0.0, 100.0, "");
                deviceDAO.addDevice(newDev);
                refreshAllData();
                JOptionPane.showMessageDialog(dlg, "Device '" + n + "' registered successfully and saved to XAMPP MySQL (ID: " + newDev.getDeviceId() + ")!", "Registration Success", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dlg, "Invalid Data Limit number. Please enter a valid number in GB.", "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Registration error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRow.add(btnCancel);
        btnRow.add(btnSubmit);
        p.add(btnRow, BorderLayout.SOUTH);

        dlg.add(p);
        dlg.setVisible(true);
    }

    private String generateRandomMac() {
        Random r = new Random();
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X", r.nextInt(256), r.nextInt(256), r.nextInt(256), r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    // =========================================================================
    // TAB 4: ADMIN CONTROLS
    // =========================================================================
    private JPanel createAdminTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 16));
        tab.setOpaque(false);
        tab.setBorder(new EmptyBorder(22, 26, 22, 26));

        tab.add(createSectionHeader("Admin Controls & Bandwidth Policy", "Configure device quotas, bandwidth limits, and speed throttling", null), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);

        // Top Split: Policy Control on Left, Network Diagnostics on Right
        JPanel topSplit = new JPanel(new GridLayout(1, 2, 16, 0));
        topSplit.setOpaque(false);

        // Left: Bandwidth & Access Control Card
        JPanel policyCard = createCardPanel(10);
        policyCard.setLayout(new BoxLayout(policyCard, BoxLayout.Y_AXIS));
        policyCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel lblPolicyTitle = new JLabel("Bandwidth & Access Control");
        lblPolicyTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPolicyTitle.setForeground(TEXT_PRIMARY);
        lblPolicyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        policyCard.add(lblPolicyTitle);
        policyCard.add(Box.createRigidArea(new Dimension(0, 12)));

        JLabel lblTarget = new JLabel("Target Device");
        lblTarget.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTarget.setForeground(TEXT_SECONDARY);
        lblTarget.setAlignmentX(Component.LEFT_ALIGNMENT);
        policyCard.add(lblTarget);
        policyCard.add(Box.createRigidArea(new Dimension(0, 4)));

        cmbAdminDevice = new JComboBox<>();
        styleModernComboBox(cmbAdminDevice);
        cmbAdminDevice.setMaximumSize(new Dimension(3000, 34));
        cmbAdminDevice.setAlignmentX(Component.LEFT_ALIGNMENT);
        cmbAdminDevice.addActionListener(e -> onAdminDeviceSelected());
        policyCard.add(cmbAdminDevice);
        policyCard.add(Box.createRigidArea(new Dimension(0, 10)));

        txtAdminDataLimit = createFormInput(policyCard, "Data Limit (GB)", "e.g. 50");
        txtAdminSpeedLimit = createFormInput(policyCard, "Bandwidth Limit (Mbps)", "e.g. 20");

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnSavePolicy = createModernButton("Save Policy", ACCENT_BLUE, Color.WHITE);
        btnSavePolicy.addActionListener(e -> saveAdminPolicy());

        btnAdminBlockToggle = createModernButton("Block Device", ACCENT_RED, Color.WHITE);
        btnAdminBlockToggle.addActionListener(e -> toggleAdminDeviceBlock());

        btnRow.add(btnSavePolicy);
        btnRow.add(btnAdminBlockToggle);
        policyCard.add(btnRow);
        policyCard.add(Box.createRigidArea(new Dimension(0, 8)));

        lblAdminMsg = new JLabel("");
        lblAdminMsg.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblAdminMsg.setForeground(ACCENT_GREEN_LIGHT);
        lblAdminMsg.setAlignmentX(Component.LEFT_ALIGNMENT);
        policyCard.add(lblAdminMsg);

        topSplit.add(policyCard);

        // Right: Engine & Database Diagnostics Panel
        JPanel diagCard = createCardPanel(10);
        diagCard.setLayout(new BoxLayout(diagCard, BoxLayout.Y_AXIS));
        diagCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel lblDiagTitle = new JLabel("Network Topology & Diagnostics");
        lblDiagTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblDiagTitle.setForeground(TEXT_PRIMARY);
        lblDiagTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagCard.add(lblDiagTitle);
        diagCard.add(Box.createRigidArea(new Dimension(0, 10)));

        lblDbStatusDiag = new JLabel("● Database: Loading...");
        lblDbStatusDiag.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDbStatusDiag.setForeground(ACCENT_GREEN_LIGHT);
        lblDbStatusDiag.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagCard.add(lblDbStatusDiag);
        diagCard.add(Box.createRigidArea(new Dimension(0, 8)));

        String hostIp = "127.0.0.1";
        try { hostIp = InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) {}
        String topSubnet = hostIp.contains(".") ? hostIp.substring(0, hostIp.lastIndexOf('.')) + ".0/24" : "10.0.0.0/24";
        lblNetworkTopologyDiag = new JTextArea(
                "● Subnet: " + topSubnet + "\n" +
                "● Host IP: " + hostIp + "\n" +
                "● DNS Filter: UDP Port 53\n" +
                "● HTTP Proxy: TCP Port 8081\n" +
                "● Mode: Standalone Java Swing Desktop"
        );
        lblNetworkTopologyDiag.setFont(new Font("Consolas", Font.PLAIN, 12));
        lblNetworkTopologyDiag.setForeground(TEXT_SECONDARY);
        lblNetworkTopologyDiag.setOpaque(false);
        lblNetworkTopologyDiag.setEditable(false);
        lblNetworkTopologyDiag.setFocusable(false);
        lblNetworkTopologyDiag.setBorder(null);
        lblNetworkTopologyDiag.setAlignmentX(Component.LEFT_ALIGNMENT);
        diagCard.add(lblNetworkTopologyDiag);
        diagCard.add(Box.createRigidArea(new Dimension(0, 14)));

        JButton btnRefreshDiag = createModernButton("Refresh Diagnostics", new Color(33, 38, 45), TEXT_PRIMARY);
        btnRefreshDiag.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRefreshDiag.addActionListener(e -> refreshDiagnostics());
        diagCard.add(btnRefreshDiag);

        topSplit.add(diagCard);
        center.add(topSplit, BorderLayout.NORTH);

        // Bottom: User Accounts Panel (Full Width)
        JPanel usersCard = createCardPanel(10);
        usersCard.setLayout(new BorderLayout(0, 10));
        usersCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel usersHeader = new JPanel(new BorderLayout());
        usersHeader.setOpaque(false);
        JLabel lblUsersTitle = new JLabel("User Accounts");
        lblUsersTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblUsersTitle.setForeground(TEXT_PRIMARY);

        JButton btnAddUser = createSmallButton("+ New User", ACCENT_BLUE);
        btnAddUser.addActionListener(e -> showAddUserDialog());
        usersHeader.add(lblUsersTitle, BorderLayout.WEST);
        usersHeader.add(btnAddUser, BorderLayout.EAST);
        usersCard.add(usersHeader, BorderLayout.NORTH);

        String[] userCols = {"Username", "Email Address"};
        modelAdminUsers = new DefaultTableModel(userCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblAdminUsers = new JTable(modelAdminUsers);
        styleModernTable(tblAdminUsers);

        JScrollPane scrollUsers = new JScrollPane(tblAdminUsers);
        scrollUsers.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollUsers.getViewport().setBackground(BG_CARD);
        scrollUsers.setPreferredSize(new Dimension(0, 140));
        usersCard.add(scrollUsers, BorderLayout.CENTER);

        JButton btnDeleteUser = createSmallButton("Delete Selected Account", ACCENT_RED);
        btnDeleteUser.addActionListener(e -> deleteAdminUser());
        usersCard.add(btnDeleteUser, BorderLayout.SOUTH);

        center.add(usersCard, BorderLayout.CENTER);

        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private void onAdminDeviceSelected() {
        int idx = cmbAdminDevice.getSelectedIndex();
        if (idx >= 0 && idx < cachedDevices.size()) {
            Device d = cachedDevices.get(idx);
            txtAdminDataLimit.setText(String.valueOf(d.getDataLimit()));
            txtAdminSpeedLimit.setText(String.valueOf(d.getBandwidthLimitMbps()));
            if (d.isBlocked()) {
                btnAdminBlockToggle.setText("Unblock Device");
                btnAdminBlockToggle.setBackground(ACCENT_BLUE);
            } else {
                btnAdminBlockToggle.setText("Block Device");
                btnAdminBlockToggle.setBackground(ACCENT_RED);
            }
        }
    }

    private void saveAdminPolicy() {
        int idx = cmbAdminDevice.getSelectedIndex();
        if (idx >= 0 && idx < cachedDevices.size()) {
            Device d = cachedDevices.get(idx);
            try {
                double dataLim = Double.parseDouble(txtAdminDataLimit.getText().trim());
                double speedLim = Double.parseDouble(txtAdminSpeedLimit.getText().trim());
                deviceDAO.setDeviceLimits(d.getDeviceId(), dataLim, speedLim);
                lblAdminMsg.setText("✓ Policy saved for " + d.getDeviceName());
                lblAdminMsg.setForeground(ACCENT_GREEN_LIGHT);
                refreshAllData();
            } catch (Exception ex) {
                lblAdminMsg.setText("✗ Invalid number format.");
                lblAdminMsg.setForeground(ACCENT_RED_LIGHT);
            }
        }
    }

    private void toggleAdminDeviceBlock() {
        int idx = cmbAdminDevice.getSelectedIndex();
        if (idx >= 0 && idx < cachedDevices.size()) {
            Device d = cachedDevices.get(idx);
            boolean newBlock = !d.isBlocked();
            deviceDAO.toggleBlockDevice(d.getDeviceId(), newBlock);
            refreshAllData();
        }
    }

    private void showAddUserDialog() {
        JDialog dlg = new JDialog(this, "Create User Account", true);
        dlg.setSize(380, 300);
        dlg.setLocationRelativeTo(this);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_CARD);
        p.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("Register New User");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 12)));

        JTextField txtU = createFormInput(p, "Username", "username");
        JTextField txtE = createFormInput(p, "Email", "user@wifi.local");
        JTextField txtP = createFormInput(p, "Password", "password");

        JButton btnSubmit = createModernButton("Create Account", ACCENT_GREEN_LIGHT, Color.WHITE);
        btnSubmit.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSubmit.addActionListener(e -> {
            String u = txtU.getText().trim();
            String em = txtE.getText().trim();
            String pw = txtP.getText().trim();
            if (u.isEmpty() || em.isEmpty() || pw.isEmpty()) return;

            Connection conn = DatabaseConnection.getConnection();
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO login (username, password, email) VALUES (?, ?, ?)")) {
                    ps.setString(1, u); ps.setString(2, pw); ps.setString(3, em);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            refreshUsers();
            dlg.dispose();
        });
        p.add(btnSubmit);

        dlg.add(p);
        dlg.setVisible(true);
    }

    private void deleteAdminUser() {
        int row = tblAdminUsers.getSelectedRow();
        if (row == -1) return;
        String u = (String) modelAdminUsers.getValueAt(row, 0);
        if (u.equalsIgnoreCase(currentUsername)) {
            JOptionPane.showMessageDialog(this, "Cannot delete currently logged in account.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM login WHERE username = ?")) {
                ps.setString(1, u); ps.executeUpdate();
            } catch (Exception ignored) {}
        }
        refreshUsers();
    }

    // =========================================================================
    // TAB 5: TRAFFIC ANOMALY DETECTION
    // =========================================================================
    private JPanel createAiTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 16));
        tab.setOpaque(false);
        tab.setBorder(new EmptyBorder(22, 26, 22, 26));

        JButton btnScan = createModernButton("Run Anomaly Scan", ACCENT_BLUE, Color.WHITE);
        btnScan.addActionListener(e -> runAiScan());

        tab.add(createSectionHeader("Traffic Anomaly Detection", "Heuristic baseline analysis, bandwidth surge detection, and unusual port activity", btnScan), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);

        // Risk Gauge Banner Card
        riskBannerCard = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        riskBannerCard.setOpaque(false);
        riskBannerCard.setPreferredSize(new Dimension(0, 84));
        riskBannerCard.setBorder(new EmptyBorder(14, 18, 14, 18));

        // Left Score Box
        JPanel scoreBox = new JPanel(new BorderLayout());
        scoreBox.setOpaque(false);
        scoreBox.setPreferredSize(new Dimension(72, 56));

        lblRiskScoreVal = new JLabel("12", SwingConstants.CENTER);
        lblRiskScoreVal.setFont(new Font("Consolas", Font.BOLD, 26));
        lblRiskScoreVal.setForeground(ACCENT_GREEN_LIGHT);

        JLabel lblScoreLbl = new JLabel("Anomaly Score", SwingConstants.CENTER);
        lblScoreLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblScoreLbl.setForeground(TEXT_MUTED);

        scoreBox.add(lblRiskScoreVal, BorderLayout.CENTER);
        scoreBox.add(lblScoreLbl, BorderLayout.SOUTH);
        riskBannerCard.add(scoreBox, BorderLayout.WEST);

        // Center Details
        JPanel bannerDetails = new JPanel();
        bannerDetails.setLayout(new BoxLayout(bannerDetails, BoxLayout.Y_AXIS));
        bannerDetails.setOpaque(false);

        lblRiskTitle = new JLabel("Network Traffic Normal");
        lblRiskTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblRiskTitle.setForeground(TEXT_PRIMARY);

        lblRiskDesc = new JLabel("All connected devices are within normal baseline usage.");
        lblRiskDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblRiskDesc.setForeground(TEXT_SECONDARY);

        bannerDetails.add(lblRiskTitle);
        bannerDetails.add(Box.createRigidArea(new Dimension(0, 4)));
        bannerDetails.add(lblRiskDesc);
        riskBannerCard.add(bannerDetails, BorderLayout.CENTER);

        center.add(riskBannerCard, BorderLayout.NORTH);

        // Flagged Traffic Anomalies & Insights Card
        JPanel listCard = createCardPanel(10);
        listCard.setLayout(new BorderLayout(0, 12));
        listCard.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setOpaque(false);
        JLabel lblListTitle = new JLabel("Flagged Traffic Anomalies & Insights");
        lblListTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblListTitle.setForeground(TEXT_PRIMARY);

        lblAiFindingsCount = createPillBadge("0 Anomalies", ACCENT_GREEN_LIGHT);
        listHeader.add(lblListTitle, BorderLayout.WEST);
        listHeader.add(lblAiFindingsCount, BorderLayout.EAST);
        listCard.add(listHeader, BorderLayout.NORTH);

        // Scrollable Feed of Anomaly Cards
        anomaliesContainer = new JPanel();
        anomaliesContainer.setLayout(new BoxLayout(anomaliesContainer, BoxLayout.Y_AXIS));
        anomaliesContainer.setBackground(BG_CARD);

        JScrollPane scrollAnomalies = new JScrollPane(anomaliesContainer);
        scrollAnomalies.setBorder(BorderFactory.createEmptyBorder());
        scrollAnomalies.getViewport().setBackground(BG_CARD);

        listCard.add(scrollAnomalies, BorderLayout.CENTER);
        center.add(listCard, BorderLayout.CENTER);

        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private void runAiScan() {
        currentAnomalies = aiDetector.detectAnomalies();
        anomaliesContainer.removeAll();

        int score = currentAnomalies.isEmpty() ? 12 : Math.min(95, 25 + currentAnomalies.size() * 25);
        lblRiskScoreVal.setText(String.valueOf(score));

        if (score > 60) {
            lblRiskScoreVal.setForeground(ACCENT_RED_LIGHT);
            lblRiskTitle.setText("High Anomaly Risk Detected");
            lblRiskDesc.setText("Found " + currentAnomalies.size() + " unusual traffic patterns (heavy upload spikes or exceeded data caps).");
            lblAiFindingsCount.setText(currentAnomalies.size() + " Anomalies Found");
            lblAiFindingsCount.setForeground(ACCENT_RED_LIGHT);
        } else if (score > 30) {
            lblRiskScoreVal.setForeground(ACCENT_AMBER);
            lblRiskTitle.setText("Moderate Traffic Variance");
            lblRiskDesc.setText("Minor deviations from baseline expectations detected.");
            lblAiFindingsCount.setText(currentAnomalies.size() + " Anomalies Found");
            lblAiFindingsCount.setForeground(ACCENT_AMBER);
        } else {
            lblRiskScoreVal.setForeground(ACCENT_GREEN_LIGHT);
            lblRiskTitle.setText("Network Traffic Normal");
            lblRiskDesc.setText("All connected devices are within normal baseline usage.");
            lblAiFindingsCount.setText("0 Anomalies");
            lblAiFindingsCount.setForeground(ACCENT_GREEN_LIGHT);
        }

        if (lblAiSidebarBadge != null) {
            lblAiSidebarBadge.setText(String.valueOf(currentAnomalies.size()));
            JButton aiBtn = navButtons.get("ai");
            if (aiBtn != null) aiBtn.repaint();
        }

        if (currentAnomalies.isEmpty()) {
            JPanel emptyBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 24));
            emptyBox.setOpaque(false);
            JLabel emptyLbl = new JLabel("✓ No Anomalies Found. All device traffic is conforming to baseline expectations.");
            emptyLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            emptyLbl.setForeground(ACCENT_GREEN_LIGHT);
            emptyBox.add(emptyLbl);
            anomaliesContainer.add(emptyBox);
        } else {
            for (AiAnomalyDetector.AnomalyResult a : currentAnomalies) {
                anomaliesContainer.add(createAnomalyCard(a));
                anomaliesContainer.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        }

        anomaliesContainer.revalidate();
        anomaliesContainer.repaint();
    }

    private JPanel createAnomalyCard(AiAnomalyDetector.AnomalyResult a) {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD_SUBTLE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setMaximumSize(new Dimension(3000, 130));

        // Top line: Badge + Anomaly Type + Risk score
        JPanel topLine = new JPanel(new BorderLayout());
        topLine.setOpaque(false);

        JPanel leftTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftTitle.setOpaque(false);

        boolean isCritical = "CRITICAL".equalsIgnoreCase(a.severity);
        JLabel badge = createPillBadge(a.severity, isCritical ? ACCENT_RED_LIGHT : ACCENT_AMBER);
        JLabel title = new JLabel(a.anomalyType);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(TEXT_PRIMARY);

        leftTitle.add(badge);
        leftTitle.add(title);

        JLabel score = new JLabel("Risk Score: " + a.riskScore + "/100");
        score.setFont(new Font("Consolas", Font.BOLD, 11));
        score.setForeground(ACCENT_BLUE);

        topLine.add(leftTitle, BorderLayout.WEST);
        topLine.add(score, BorderLayout.EAST);
        card.add(topLine, BorderLayout.NORTH);

        // Center line: Target Device + AI explanation
        JPanel centerBody = new JPanel();
        centerBody.setLayout(new BoxLayout(centerBody, BoxLayout.Y_AXIS));
        centerBody.setOpaque(false);

        JLabel target = new JLabel("Device: " + a.deviceName + "  •  IP: " + a.ipAddress + "  •  MAC: " + a.macAddress);
        target.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        target.setForeground(TEXT_SECONDARY);

        JLabel aiReason = new JLabel("AI Insight: " + a.aiExplanation);
        aiReason.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        aiReason.setForeground(ACCENT_CYAN);

        centerBody.add(target);
        centerBody.add(Box.createRigidArea(new Dimension(0, 3)));
        centerBody.add(aiReason);
        card.add(centerBody, BorderLayout.CENTER);

        // Bottom line: Action buttons
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomActions.setOpaque(false);

        JButton btnBlock = createSmallButton("Block Device", ACCENT_RED);
        btnBlock.addActionListener(e -> {
            deviceDAO.toggleBlockDevice(a.deviceId, true);
            refreshAllData();
            runAiScan();
            JOptionPane.showMessageDialog(this, "Device '" + a.deviceName + "' has been blocked.", "Action Executed", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton btnThrottle = createSmallButton("Apply 2 Mbps Limit", ACCENT_BLUE);
        btnThrottle.addActionListener(e -> {
            deviceDAO.setDeviceLimits(a.deviceId, 20.0, 2.0);
            refreshAllData();
            runAiScan();
            JOptionPane.showMessageDialog(this, "2 Mbps throttle applied to '" + a.deviceName + "'.", "Action Executed", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton btnDismiss = createSmallButton("Dismiss", new Color(43, 51, 64));
        btnDismiss.addActionListener(e -> {
            anomaliesContainer.remove(card);
            anomaliesContainer.revalidate();
            anomaliesContainer.repaint();
        });

        bottomActions.add(btnBlock);
        bottomActions.add(btnThrottle);
        bottomActions.add(btnDismiss);
        card.add(bottomActions, BorderLayout.SOUTH);

        return card;
    }

    // =========================================================================
    // TAB 6: WEBSITE BLOCKING & FIREWALL
    // =========================================================================
    private JPanel createBlockingTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 16));
        tab.setOpaque(false);
        tab.setBorder(new EmptyBorder(22, 26, 22, 26));

        // Top buttons: Simulate Test Event, Clear Logs & Admin Relaunch
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);

        if (!WindowsFirewallManager.isElevated()) {
            JButton btnRelaunch = createModernButton("⚡ Relaunch as Admin", ACCENT_AMBER, Color.BLACK);
            btnRelaunch.setToolTipText("Restart application with Administrator privileges to enable Windows Firewall & Hosts file blocking");
            btnRelaunch.addActionListener(e -> WindowsFirewallManager.relaunchAsAdmin());
            actions.add(btnRelaunch);
        }

        JButton btnSimulate = createModernButton("Simulate Test Event", new Color(33, 38, 45), ACCENT_CYAN);
        btnSimulate.addActionListener(e -> simulateTestEvent());

        JButton btnClearLogs = createModernButton("Clear Logs", new Color(33, 38, 45), TEXT_SECONDARY);
        btnClearLogs.addActionListener(e -> {
            blockedLogDAO.clearLogs();
            refreshBlockedLogs();
        });

        actions.add(btnSimulate);
        actions.add(btnClearLogs);

        tab.add(createSectionHeader("Real-Time Website Blocking & Network Firewall", "Network-level domain filtering for connected clients via Local DNS Filter, HTTP Proxy, and Windows Firewall", actions), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        // 1. Interceptor Status Cards Grid (5 items)
        JPanel interceptorGrid = new JPanel(new GridLayout(1, 5, 12, 0));
        interceptorGrid.setOpaque(false);
        interceptorGrid.setPreferredSize(new Dimension(0, 72));

        interceptorGrid.add(createInterceptorCard("DNS Filter Engine", lblDnsServerStatus = new JLabel("UDP Port 53 (Active)"), "Hotspot DNS Gateway", ACCENT_GREEN_LIGHT));
        interceptorGrid.add(createInterceptorCard("HTTP/HTTPS Proxy", lblProxyServerStatus = new JLabel("Port 8081 (Active)"), "TCP Intercept Tunnel", ACCENT_GREEN_LIGHT));
        interceptorGrid.add(createInterceptorCard("Windows Hotspot IP", lblHotspotIp = new JLabel("192.168.137.1"), "Assign as Phone B DNS", ACCENT_BLUE));
        interceptorGrid.add(createInterceptorCard("Active Block Rules", lblBlockedRulesCount = new JLabel("0 Domains"), "Real-Time Enforcement", ACCENT_PURPLE));
        boolean isElev = WindowsFirewallManager.isElevated();
        interceptorGrid.add(createInterceptorCard("Windows Firewall", new JLabel(isElev ? "Kernel Mode (Active)" : "Limited Mode"), isElev ? "Hosts & Netsh Active" : "Requires Admin Privileges", isElev ? ACCENT_GREEN_LIGHT : ACCENT_AMBER));

        // 2. Setup Guide Banner Card
        JPanel phoneGuideCard = createCardPanel(10);
        phoneGuideCard.setLayout(new BorderLayout(0, 8));
        phoneGuideCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblGuideTitle = new JLabel("Network Blocking & Client Setup Guide");
        lblGuideTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblGuideTitle.setForeground(ACCENT_CYAN);

        JTextArea txtGuideText = new JTextArea(
                "• Host PC (Laptop): Blocked websites are synchronized to C:\\Windows\\System32\\drivers\\etc\\hosts for instant browser-level blocking.\n" +
                "• Method 1 (Phone / Client - Recommended): Set Wi-Fi Proxy to Manual -> Host: 192.168.137.1 | Port: 8081 (Intercepts HTTP & HTTPS directly).\n" +
                "• Method 2 (Phone / Client - DNS): Set Router or Phone DNS to this PC (Port 53) to drop blocked domain lookups.\n" +
                "• Device Blocking: Clicking 'Block Device' completely blocks that device's IP at the Windows Firewall and drops all traffic."
        );
        txtGuideText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        txtGuideText.setForeground(TEXT_SECONDARY);
        txtGuideText.setOpaque(false);
        txtGuideText.setEditable(false);
        txtGuideText.setFocusable(false);
        txtGuideText.setLineWrap(true);
        txtGuideText.setWrapStyleWord(true);
        txtGuideText.setBorder(null);

        phoneGuideCard.add(lblGuideTitle, BorderLayout.NORTH);
        phoneGuideCard.add(txtGuideText, BorderLayout.CENTER);

        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setOpaque(false);
        topContainer.add(interceptorGrid, BorderLayout.NORTH);
        topContainer.add(phoneGuideCard, BorderLayout.SOUTH);
        center.add(topContainer, BorderLayout.NORTH);

        // 3. Two-Column Split (Left: Block Form + Blocked Websites; Right: Real-time Live Log Feed)
        JPanel splitCols = new JPanel(new GridLayout(1, 2, 16, 0));
        splitCols.setOpaque(false);

        // Left Column
        JPanel leftCol = new JPanel(new BorderLayout(0, 12));
        leftCol.setOpaque(false);

        // Block Domain Input Card
        JPanel addDomainCard = createCardPanel(10);
        addDomainCard.setLayout(new BoxLayout(addDomainCard, BoxLayout.Y_AXIS));
        addDomainCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblAddTitle = new JLabel("Block a Website Domain");
        lblAddTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblAddTitle.setForeground(TEXT_PRIMARY);
        headerRow.add(lblAddTitle, BorderLayout.WEST);

        JLabel tagRule = new JLabel("Network Rule");
        tagRule.setFont(new Font("Segoe UI", Font.BOLD, 10));
        tagRule.setForeground(TEXT_SECONDARY);
        tagRule.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        headerRow.add(tagRule, BorderLayout.EAST);
        addDomainCard.add(headerRow);
        addDomainCard.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtBlockDomainInput = createModernTextField("e.g. youtube.com, instagram.com");
        txtBlockDomainInput.setPreferredSize(new Dimension(170, 34));

        cmbBlockCategory = new JComboBox<>(new String[]{"Social Media", "Streaming", "Gaming", "Gambling", "Security Risk", "General"});
        styleModernComboBox(cmbBlockCategory);
        cmbBlockCategory.setPreferredSize(new Dimension(135, 34));

        JButton btnSubmitBlock = createSmallButton("Block Website", ACCENT_RED);
        btnSubmitBlock.setIcon(new NavIcon("blocking", 13, Color.WHITE));
        btnSubmitBlock.setIconTextGap(6);
        btnSubmitBlock.setPreferredSize(new Dimension(125, 34));
        btnSubmitBlock.addActionListener(e -> submitBlockWebsite());

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightControls.setOpaque(false);
        rightControls.add(cmbBlockCategory);
        rightControls.add(btnSubmitBlock);

        inputRow.add(txtBlockDomainInput, BorderLayout.CENTER);
        inputRow.add(rightControls, BorderLayout.EAST);
        addDomainCard.add(inputRow);

        lblBlockMsg = new JLabel("");
        lblBlockMsg.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblBlockMsg.setForeground(ACCENT_GREEN_LIGHT);
        lblBlockMsg.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblBlockMsg.setBorder(new EmptyBorder(6, 2, 0, 0));
        addDomainCard.add(lblBlockMsg);

        leftCol.add(addDomainCard, BorderLayout.NORTH);

        // Blocked Websites Table Card
        JPanel blockedListCard = createCardPanel(10);
        blockedListCard.setLayout(new BorderLayout(0, 10));
        blockedListCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel tableHeaderPanel = new JPanel(new BorderLayout());
        tableHeaderPanel.setOpaque(false);
        JLabel lblListTitle = new JLabel("Currently Blocked Websites");
        lblListTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblListTitle.setForeground(TEXT_PRIMARY);
        tableHeaderPanel.add(lblListTitle, BorderLayout.WEST);

        JLabel tagRuleSub = new JLabel("Enforced on DNS & Proxy");
        tagRuleSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tagRuleSub.setForeground(TEXT_SECONDARY);
        tableHeaderPanel.add(tagRuleSub, BorderLayout.EAST);
        blockedListCard.add(tableHeaderPanel, BorderLayout.NORTH);

        String[] restCols = {"Domain", "Category", "Status", "Actions"};
        modelBlockedWebsites = new DefaultTableModel(restCols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblBlockedWebsites = new JTable(modelBlockedWebsites);
        styleModernTable(tblBlockedWebsites);
        tblBlockedWebsites.getColumnModel().getColumn(0).setPreferredWidth(170);
        tblBlockedWebsites.getColumnModel().getColumn(1).setPreferredWidth(110);
        tblBlockedWebsites.getColumnModel().getColumn(2).setPreferredWidth(85);
        tblBlockedWebsites.getColumnModel().getColumn(3).setPreferredWidth(85);

        tblBlockedWebsites.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tblBlockedWebsites.rowAtPoint(e.getPoint());
                int col = tblBlockedWebsites.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 3) {
                    showRestrictionActionMenu(e.getComponent(), e.getX(), e.getY(), row);
                }
            }
        });

        JScrollPane scrollRest = new JScrollPane(tblBlockedWebsites);
        scrollRest.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollRest.getViewport().setBackground(BG_CARD);
        scrollRest.setBackground(BG_CARD);
        blockedListCard.add(scrollRest, BorderLayout.CENTER);

        leftCol.add(blockedListCard, BorderLayout.CENTER);
        splitCols.add(leftCol);

        // Right Column: Real-Time Blocked Activity Feed
        JPanel liveLogCard = createCardPanel(10);
        liveLogCard.setLayout(new BorderLayout(0, 10));
        liveLogCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel liveLogHeader = new JPanel(new BorderLayout());
        liveLogHeader.setOpaque(false);

        JPanel redPulseBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        redPulseBox.setOpaque(false);

        JPanel redDot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT_RED_LIGHT);
                g2.fillOval(0, 2, 8, 8);
                g2.dispose();
            }
        };
        redDot.setOpaque(false);
        redDot.setPreferredSize(new Dimension(8, 12));

        JLabel lblFeedTitle = new JLabel("Real-Time Blocked Activity Log");
        lblFeedTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblFeedTitle.setForeground(TEXT_PRIMARY);

        redPulseBox.add(redDot);
        redPulseBox.add(lblFeedTitle);

        JLabel lblLiveBadge = createPillBadge("Live Feed Active", ACCENT_GREEN_LIGHT);
        liveLogHeader.add(redPulseBox, BorderLayout.WEST);
        liveLogHeader.add(lblLiveBadge, BorderLayout.EAST);

        JLabel lblFeedSub = new JLabel("Incoming blocked requests from Phone B appear below in real time.");
        lblFeedSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblFeedSub.setForeground(TEXT_MUTED);

        JPanel liveHeaderBox = new JPanel(new BorderLayout(0, 4));
        liveHeaderBox.setOpaque(false);
        liveHeaderBox.add(liveLogHeader, BorderLayout.NORTH);
        liveHeaderBox.add(lblFeedSub, BorderLayout.SOUTH);
        liveLogCard.add(liveHeaderBox, BorderLayout.NORTH);

        liveFeedContainer = new JPanel();
        liveFeedContainer.setLayout(new BoxLayout(liveFeedContainer, BoxLayout.Y_AXIS));
        liveFeedContainer.setBackground(BG_CARD);

        JScrollPane scrollLiveFeed = new JScrollPane(liveFeedContainer);
        scrollLiveFeed.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollLiveFeed.getViewport().setBackground(BG_CARD);
        liveLogCard.add(scrollLiveFeed, BorderLayout.CENTER);

        splitCols.add(liveLogCard);
        center.add(splitCols, BorderLayout.CENTER);

        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel createInterceptorCard(String label, JLabel valLbl, String sub, Color accent) {
        JPanel card = createCardPanel(8);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel lblL = new JLabel(label);
        lblL.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblL.setForeground(TEXT_SECONDARY);

        valLbl.setFont(new Font("Consolas", Font.BOLD, 13));
        valLbl.setForeground(accent);

        JLabel lblS = new JLabel(sub);
        lblS.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblS.setForeground(TEXT_MUTED);

        card.add(lblL);
        card.add(Box.createRigidArea(new Dimension(0, 3)));
        card.add(valLbl);
        card.add(Box.createRigidArea(new Dimension(0, 2)));
        card.add(lblS);
        return card;
    }

    private void submitBlockWebsite() {
        String domain = txtBlockDomainInput.getText().trim();
        String cat = (String) cmbBlockCategory.getSelectedItem();
        if (domain.isEmpty()) {
            lblBlockMsg.setText("✗ Enter a valid domain (e.g. youtube.com)");
            lblBlockMsg.setForeground(ACCENT_RED_LIGHT);
            return;
        }

        restrictionDAO.addRestriction(new WebsiteRestriction(0, 0, domain, cat, "BLOCK", "BLOCKED", null));
        txtBlockDomainInput.setText("");
        lblBlockMsg.setText("✓ " + domain + " blocked on network");
        lblBlockMsg.setForeground(ACCENT_GREEN_LIGHT);
        refreshRestrictions();
    }

    private void showRestrictionActionMenu(Component invoker, int x, int y, int row) {
        String domain = (String) modelBlockedWebsites.getValueAt(row, 0);
        String status = (String) modelBlockedWebsites.getValueAt(row, 2);

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_CARD);
        menu.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        boolean isBlocked = "BLOCKED".equalsIgnoreCase(status);
        JMenuItem itemToggle = new JMenuItem(isBlocked ? "🟢 Unblock Domain" : "🚫 Re-Block Domain");
        itemToggle.setForeground(isBlocked ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT);
        itemToggle.setBackground(BG_CARD);
        itemToggle.addActionListener(e -> {
            for (WebsiteRestriction r : restrictionDAO.getAllRestrictions()) {
                if (r.getDomainName().equalsIgnoreCase(domain)) {
                    restrictionDAO.toggleRestrictionStatus(r.getRestrictionId(), isBlocked ? "ALLOWED" : "BLOCKED");
                    break;
                }
            }
            refreshRestrictions();
        });

        JMenuItem itemDelete = new JMenuItem("🗑️ Delete Rule");
        itemDelete.setForeground(ACCENT_RED_LIGHT);
        itemDelete.setBackground(BG_CARD);
        itemDelete.addActionListener(e -> {
            for (WebsiteRestriction r : restrictionDAO.getAllRestrictions()) {
                if (r.getDomainName().equalsIgnoreCase(domain)) {
                    restrictionDAO.removeRestriction(r.getRestrictionId());
                    break;
                }
            }
            refreshRestrictions();
        });

        menu.add(itemToggle);
        menu.add(itemDelete);
        menu.show(invoker, x, y);
    }

    private void simulateTestEvent() {
        blockedLogDAO.recordBlockedRequest("youtube.com", "192.168.137.45", "Test Phone (Phone B)", "BLOCKED (Simulated)");
        pollBlockedEvents();
    }

    private void pollBlockedEvents() {
        List<BlockedLogDAO.BlockedEvent> logs = blockedLogDAO.getRecentLogs(20);
        if (logs.isEmpty()) return;

        long latestId = logs.get(0).getLogId();
        if (latestId == previousLatestLogId) return; // No new logs
        previousLatestLogId = latestId;

        SwingUtilities.invokeLater(() -> renderLiveFeed(logs));
    }

    private void renderLiveFeed(List<BlockedLogDAO.BlockedEvent> logs) {
        if (liveFeedContainer == null) return;
        liveFeedContainer.removeAll();

        int count = 0;
        for (BlockedLogDAO.BlockedEvent ev : logs) {
            final boolean isNewest = (count == 0);
            JPanel item = new JPanel(new BorderLayout(0, 4)) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isNewest ? new Color(207, 34, 46, 25) : BG_CARD_SUBTLE);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                    g2.setColor(isNewest ? new Color(207, 34, 46, 90) : BORDER_COLOR);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                    g2.dispose();
                }
            };
            item.setOpaque(false);
            item.setBorder(new EmptyBorder(8, 10, 8, 10));
            item.setMaximumSize(new Dimension(3000, 68));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            JPanel leftBadges = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            leftBadges.setOpaque(false);
            JLabel b = createPillBadge("BLOCKED", ACCENT_RED_LIGHT);
            JLabel time = new JLabel(ev.getTimestamp());
            time.setFont(new Font("Consolas", Font.PLAIN, 10));
            time.setForeground(TEXT_MUTED);
            leftBadges.add(b);
            leftBadges.add(time);

            JLabel ip = new JLabel(ev.getClientIp());
            ip.setFont(new Font("Consolas", Font.PLAIN, 10));
            ip.setForeground(TEXT_SECONDARY);

            top.add(leftBadges, BorderLayout.WEST);
            top.add(ip, BorderLayout.EAST);
            item.add(top, BorderLayout.NORTH);

            JLabel dom = new JLabel(ev.getDomainName());
            dom.setIcon(new NavIcon("blocking", 14, ACCENT_RED_LIGHT));
            dom.setIconTextGap(6);
            dom.setFont(new Font("Segoe UI", Font.BOLD, 12));
            dom.setForeground(TEXT_PRIMARY);

            JLabel meta = new JLabel("Device: " + ev.getDeviceName() + "  •  Action: " + ev.getAction());
            meta.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            meta.setForeground(TEXT_MUTED);

            JPanel centerBox = new JPanel(new BorderLayout(0, 2));
            centerBox.setOpaque(false);
            centerBox.add(dom, BorderLayout.NORTH);
            centerBox.add(meta, BorderLayout.SOUTH);
            item.add(centerBox, BorderLayout.CENTER);

            liveFeedContainer.add(item);
            liveFeedContainer.add(Box.createRigidArea(new Dimension(0, 6)));
            count++;
        }

        liveFeedContainer.revalidate();
        liveFeedContainer.repaint();
    }

    // =========================================================================
    // REFRESH & SYNCHRONIZATION LOGIC
    // =========================================================================
    private void refreshAllData() {
        cachedDevices = deviceDAO.getAllDevices();
        List<UsageRecord> usageRecords = usageDAO.getAllUsageRecords();

        // 1. Dashboard Stats
        Map<String, Double> split = usageDAO.getOverallTrafficSplit();
        double totalGb = split.getOrDefault("total", 0.0);
        double ulGb = split.getOrDefault("upload", 0.0);
        double dlGb = split.getOrDefault("download", 0.0);
        double todayGb = usageDAO.getTodayTotalUsage();

        // 2. Doughnut Chart Breakdown
        if (doughnutChart != null) {
            Map<Integer, Double> devUsageMap = new HashMap<>();
            for (UsageRecord r : usageRecords) {
                devUsageMap.put(r.getDeviceId(), devUsageMap.getOrDefault(r.getDeviceId(), 0.0) + r.getDataUsed());
            }
            Map<String, Double> breakdown = new LinkedHashMap<>();
            for (Device d : cachedDevices) {
                double u = devUsageMap.getOrDefault(d.getDeviceId(), 0.0);
                if (u <= 0.0) {
                    u = d.getDownloadGb() + d.getUploadGb();
                }
                if (u > 0) breakdown.put(d.getDeviceName(), u);
            }
            if (totalGb <= 0.0) {
                for (double val : breakdown.values()) totalGb += val;
            }
            doughnutChart.setUsageBreakdown(breakdown, totalGb);
        }

        if (lblStatTotalVal != null) lblStatTotalVal.setText(String.format("%.2f GB", totalGb));
        if (lblStatSplitVal != null) {
            if (dlGb == 0.0 && ulGb == 0.0 && !cachedDevices.isEmpty()) {
                Device d0 = cachedDevices.get(0);
                lblStatSplitVal.setText(String.format("↓ %.2f / ↑ %.2f GB", d0.getDownloadGb(), d0.getUploadGb()));
            } else {
                lblStatSplitVal.setText(String.format("↓ %.2f / ↑ %.2f GB", dlGb, ulGb));
            }
        }
        if (lblStatDevicesVal != null) lblStatDevicesVal.setText(cachedDevices.size() + " Devices");
        if (lblStatDevicesSub != null) lblStatDevicesSub.setText(cachedDevices.size() + " total registered");
        if (lblStatTodayVal != null) lblStatTodayVal.setText(String.format("%.2f GB", todayGb > 0 ? todayGb : totalGb));

        // 3. Devices Registry Table
        if (modelDevices != null) {
            modelDevices.setRowCount(0);
            for (Device d : cachedDevices) {
                String statusPill = "Connected".equalsIgnoreCase(d.getStatus()) ? "● Connected" : "○ Disconnected";
                String traffic = String.format("↓ %.2f GB • ↑ %.2f GB", d.getDownloadGb(), d.getUploadGb());
                String cap = String.format("%.1f GB", d.getDataLimit());
                String action = (d.isBlocked() ? "Unblock" : "Block") + " | Delete";

                modelDevices.addRow(new Object[]{
                        "#" + d.getDeviceId(),
                        d.getDeviceName() + " (" + d.getDeviceType() + ")",
                        d.getOwnerName(),
                        d.getIpAddress(),
                        d.getMacAddress(),
                        statusPill,
                        traffic,
                        cap,
                        action
                });
            }
        }

        // 4. Usage History & Reports Analytics (Cards, Device Table, Top Consumers, Network Summary)
        refreshHistoryAnalytics();

        // 5. Admin Device Dropdown
        if (cmbAdminDevice != null && cmbAdminDevice.getItemCount() != cachedDevices.size()) {
            cmbAdminDevice.removeAllItems();
            for (Device d : cachedDevices) {
                cmbAdminDevice.addItem("#" + d.getDeviceId() + " - " + d.getDeviceName() + " (" + d.getIpAddress() + ")" + (d.isBlocked() ? " [BLOCKED]" : ""));
            }
            onAdminDeviceSelected();
        }

        // 5. Restrictions & Diagnostics
        refreshRestrictions();
        refreshUsers();
        refreshDiagnostics();
    }

    private void refreshRestrictions() {
        List<WebsiteRestriction> list = restrictionDAO.getAllRestrictions();
        long blockedCount = list.stream().filter(r -> "BLOCKED".equalsIgnoreCase(r.getStatus())).count();
        if (lblBlockedRulesCount != null) {
            lblBlockedRulesCount.setText(blockedCount + " Domains");
        }
        if (lblSidebarRulesVal != null) {
            lblSidebarRulesVal.setText(blockedCount + " Rules");
        }
        if (modelBlockedWebsites != null) {
            modelBlockedWebsites.setRowCount(0);
            for (WebsiteRestriction r : list) {
                modelBlockedWebsites.addRow(new Object[]{
                        r.getDomainName(),
                        r.getCategory(),
                        r.getStatus(),
                        "Options..."
                });
            }
        }
    }

    private void refreshBlockedLogs() {
        List<BlockedLogDAO.BlockedEvent> logs = blockedLogDAO.getRecentLogs(20);
        renderLiveFeed(logs);
    }

    private void refreshUsers() {
        if (modelAdminUsers == null) return;
        modelAdminUsers.setRowCount(0);
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT username, email FROM login");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    modelAdminUsers.addRow(new Object[]{rs.getString("username"), rs.getString("email")});
                }
            } catch (Exception ignored) {}
        }
        if (modelAdminUsers.getRowCount() == 0) {
            modelAdminUsers.addRow(new Object[]{currentUsername, currentUsername + "@wifi.local"});
        }
    }

    private void refreshDiagnostics() {
        boolean isMysql = DatabaseConnection.isMysqlAvailable();
        if (lblDbStatusDiag != null) {
            lblDbStatusDiag.setText(isMysql ? "● Database: Active MySQL Connection (localhost:3306)" : "● Database: In-Memory High-Speed Cache (Fallback)");
            lblDbStatusDiag.setForeground(isMysql ? ACCENT_GREEN_LIGHT : ACCENT_AMBER);
        }
        boolean dnsOn = Main.getDnsFilterServer() != null && Main.getDnsFilterServer().isRunning();
        if (lblDnsServerStatus != null) {
            lblDnsServerStatus.setText(dnsOn ? "UDP Port 53 (Active)" : "UDP Port 53 (Inactive)");
            lblDnsServerStatus.setForeground(dnsOn ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT);
        }
        boolean proxyOn = Main.getHttpFilterProxy() != null && Main.getHttpFilterProxy().isRunning();
        if (lblProxyServerStatus != null) {
            lblProxyServerStatus.setText(proxyOn ? "Port 8081 (Active)" : "Port 8081 (Inactive)");
            lblProxyServerStatus.setForeground(proxyOn ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT);
        }
        if (lblSidebarDnsVal != null) {
            lblSidebarDnsVal.setText(dnsOn ? "UDP:53" : "Off");
            lblSidebarDnsVal.setForeground(dnsOn ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT);
        }
        if (lblSidebarProxyVal != null) {
            lblSidebarProxyVal.setText(proxyOn ? "TCP:8081" : "Off");
            lblSidebarProxyVal.setForeground(proxyOn ? ACCENT_GREEN_LIGHT : ACCENT_RED_LIGHT);
        }
    }

    // =========================================================================
    // WORKER: SPEEDMETER & TELEMETRY
    // =========================================================================
    private void startSpeedmeter() {
        long[] initial = getNetworkBytes();
        lastReceivedBytes = initial[0];
        lastSentBytes = initial[1];
        lastSpeedTimestamp = System.currentTimeMillis();

        Thread worker = new Thread(() -> {
            Random rng = new Random();
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }

                long[] curr = getNetworkBytes();
                long now = System.currentTimeMillis();
                long dt = now - lastSpeedTimestamp;
                if (dt <= 0) dt = 1000;

                long rxDiff = curr[0] - lastReceivedBytes;
                long txDiff = curr[1] - lastSentBytes;
                if (rxDiff < 0) rxDiff = 0;
                if (txDiff < 0) txDiff = 0;

                lastReceivedBytes = curr[0];
                lastSentBytes = curr[1];
                lastSpeedTimestamp = now;

                double dlMbps = (rxDiff * 8.0) / (dt * 1024.0 * 1.024);
                double ulMbps = (txDiff * 8.0) / (dt * 1024.0 * 1.024);

                // Real network ping latency measurement
                int ping = 12;
                try (java.net.Socket s = new java.net.Socket()) {
                    long pingStart = System.currentTimeMillis();
                    s.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 300);
                    ping = (int) (System.currentTimeMillis() - pingStart);
                } catch (Exception ignored) {
                    ping = 10;
                }

                final double fDl = dlMbps;
                final double fUl = ulMbps;
                final int fPing = ping;

                accumulatedGb += (rxDiff + txDiff) / (1024.0 * 1024.0 * 1024.0);
                loopCounter++;
                if (loopCounter % 10 == 0) {
                    new Thread(() -> {
                        deviceDAO.syncNetworkDevicesFromArp();
                    }).start();
                }
                if (loopCounter % 5 == 0 && accumulatedGb > 0 && hostDeviceId != -1) {
                    final double writeVal = accumulatedGb;
                    final double rxChunk = rxDiff / (1024.0 * 1024.0 * 1024.0);
                    final double txChunk = txDiff / (1024.0 * 1024.0 * 1024.0);
                    accumulatedGb = 0.0;
                    new Thread(() -> {
                        usageDAO.addUsageRecord(hostDeviceId, LocalDate.now().toString(), writeVal, txChunk, rxChunk);
                        SwingUtilities.invokeLater(this::refreshAllData);
                    }).start();
                }

                SwingUtilities.invokeLater(() -> {
                    if (lblDashDl != null) lblDashDl.setText(String.format("%.2f Mbps", fDl));
                    if (lblDashUl != null) lblDashUl.setText(String.format("%.2f Mbps", fUl));
                    if (lblDashPing != null) lblDashPing.setText(fPing + " ms");
                    if (lblTopPingVal != null) lblTopPingVal.setText(fPing + " ms");
                    if (lblSidebarPingVal != null) lblSidebarPingVal.setText("Latency:  " + fPing + " ms");
                    if (speedChart != null) speedChart.addDataPoint(fDl, fUl);
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private long[] getNetworkBytes() {
        return DeviceDAO.getRealNetworkBytes();
    }

    private void initHostDevice() {
        try {
            String rawHost = InetAddress.getLocalHost().getHostName();
            String hostName = cleanDeviceName(rawHost, "127.0.0.1");

            for (Device d : deviceDAO.getAllDevices()) {
                if (d.getDeviceId() == 1 || d.getDeviceName().equalsIgnoreCase(hostName) || d.getDeviceName().equalsIgnoreCase(rawHost)) {
                    hostDeviceId = d.getDeviceId();
                    return;
                }
            }
            Device autoDev = new Device(0, hostName, currentUsername, "Laptop", 50.0);
            deviceDAO.addDevice(autoDev);
            for (Device d : deviceDAO.getAllDevices()) {
                if (d.getDeviceId() == 1 || d.getDeviceName().equalsIgnoreCase(hostName) || d.getDeviceName().equalsIgnoreCase(rawHost)) {
                    hostDeviceId = d.getDeviceId();
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private String cleanDeviceName(String hostname, String ip) {
        if (hostname == null || hostname.isEmpty() || hostname.equalsIgnoreCase(ip)) {
            return "Device (" + ip + ")";
        }
        String c = hostname.replace(".local", "").replace(".home", "").replace(".lan", "").replace("-", " ").replace("_", " ");
        String[] words = c.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private String guessDeviceType(String hostname) {
        String n = hostname.toLowerCase();
        if (n.contains("phone") || n.contains("android") || n.contains("iphone") || n.contains("mobile")) return "Mobile";
        if (n.contains("tv") || n.contains("smart")) return "Smart TV";
        if (n.contains("desktop") || n.contains("pc")) return "Desktop";
        return "Laptop";
    }

    // =========================================================================
    // REUSABLE MODERN UI COMPONENTS & STYLING
    // =========================================================================
    public static JPanel createCardPanel(int radius) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        return card;
    }

    private JPanel createSectionHeader(String heading, String subtitle, JComponent rightAction) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JLabel h = new JLabel(heading);
        h.setFont(new Font("Segoe UI", Font.BOLD, 22));
        h.setForeground(TEXT_PRIMARY);

        JLabel s = new JLabel(subtitle);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        s.setForeground(TEXT_SECONDARY);

        left.add(h);
        left.add(Box.createRigidArea(new Dimension(0, 3)));
        left.add(s);
        header.add(left, BorderLayout.WEST);

        if (rightAction != null) {
            header.add(rightAction, BorderLayout.EAST);
        }

        return header;
    }

    public static JLabel createPillBadge(String text, Color accent) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 25));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setForeground(accent);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setBorder(new EmptyBorder(3, 8, 3, 8));
        return l;
    }

    public static JButton createModernButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color current = getModel().isRollover() ? new Color(Math.min(255, bg.getRed() + 20), Math.min(255, bg.getGreen() + 20), Math.min(255, bg.getBlue() + 20)) : bg;
                g2.setColor(current);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(fg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        return btn;
    }

    public static JButton createSmallButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color current = getModel().isRollover() ? new Color(Math.min(255, accent.getRed() + 25), Math.min(255, accent.getGreen() + 25), Math.min(255, accent.getBlue() + 25)) : accent;
                g2.setColor(current);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(4, 10, 4, 10));
        return btn;
    }

    private JButton createSegmentButton(String text, boolean active) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(active ? Color.WHITE : TEXT_SECONDARY);
        btn.setBackground(active ? ACCENT_BLUE : BG_CARD_SUBTLE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 30));
        return btn;
    }

    public static JTextField createModernTextField(String placeholder) {
        JTextField txt = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont().deriveFont(Font.ITALIC, 12f));
                    Insets insets = getInsets();
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(placeholder, insets.left + 2, y);
                    g2.dispose();
                }
            }
        };
        txt.setBackground(BG_INPUT);
        txt.setForeground(TEXT_PRIMARY);
        txt.setCaretColor(TEXT_PRIMARY);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        return txt;
    }

    private JTextField createFormInput(JPanel parent, String labelText, String placeholder) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(TEXT_SECONDARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createRigidArea(new Dimension(0, 4)));

        JTextField txt = createModernTextField(placeholder);
        txt.setMaximumSize(new Dimension(3000, 34));
        txt.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(txt);
        parent.add(Box.createRigidArea(new Dimension(0, 10)));
        return txt;
    }

    public static void styleModernTable(JTable table) {
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_SUBTLE);
        table.setRowHeight(36);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setSelectionBackground(new Color(33, 42, 56));
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 36));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, isSelected, hasFocus, row, col);
                l.setOpaque(true);
                l.setBackground(new Color(22, 27, 34)); // Dark background matching theme
                l.setForeground(TEXT_SECONDARY);
                l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                        BorderFactory.createEmptyBorder(0, 12, 0, 12)
                ));
                return l;
            }
        });

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, isSelected, hasFocus, row, col);
                c.setBackground(isSelected ? new Color(33, 42, 56) : (row % 2 == 0 ? BG_CARD : BG_CARD_SUBTLE));
                c.setForeground(TEXT_PRIMARY);
                if (c instanceof JLabel) {
                    JLabel l = (JLabel) c;
                    l.setBorder(new EmptyBorder(0, 12, 0, 12));
                    String val = String.valueOf(v);
                    if (val.startsWith("● Connected")) {
                        l.setForeground(ACCENT_GREEN_LIGHT);
                        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    } else if (val.startsWith("○ Disconnected")) {
                        l.setForeground(TEXT_MUTED);
                    } else if (val.equalsIgnoreCase("BLOCKED") || val.contains("Unblock")) {
                        l.setForeground(new Color(248, 81, 73));
                        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    } else if (val.equals("Options...") || val.equals("Manage")) {
                        l.setForeground(ACCENT_BLUE);
                        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                        l.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    } else {
                        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    }
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
    }

    public static <T> void styleModernComboBox(JComboBox<T> cmb) {
        cmb.setBackground(BG_INPUT);
        cmb.setForeground(TEXT_PRIMARY);
        cmb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cmb.setOpaque(false);
        cmb.setFocusable(false);
        cmb.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(BG_INPUT);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(TEXT_SECONDARY);
                        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2 - 1;
                        int cy = getHeight() / 2;
                        g2.drawLine(cx - 3, cy - 2, cx, cy + 2);
                        g2.drawLine(cx, cy + 2, cx + 3, cy - 2);
                        g2.dispose();
                    }
                };
                btn.setBorder(BorderFactory.createEmptyBorder());
                btn.setContentAreaFilled(false);
                btn.setFocusPainted(false);
                btn.setOpaque(false);
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                return btn;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_INPUT);
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.dispose();
            }
        });
        cmb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 2)
        ));
        cmb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setFont(new Font("Segoe UI", Font.BOLD, 11));
                if (index == -1) {
                    l.setOpaque(false);
                    l.setForeground(TEXT_PRIMARY);
                    l.setBorder(new EmptyBorder(1, 6, 1, 2));
                } else {
                    l.setOpaque(true);
                    l.setBackground(isSelected ? new Color(38, 48, 64) : BG_CARD);
                    l.setForeground(isSelected ? Color.WHITE : TEXT_PRIMARY);
                    l.setBorder(new EmptyBorder(6, 10, 6, 10));
                }
                return l;
            }
        });
    }

    // =========================================================================
    // 3. CUSTOM-DRAWN 2D CHARTS (PURE JAVA GRAPHICS2D)
    // =========================================================================

    // 1. Doughnut Chart Panel (Usage by Device Distribution)
    public static class DoughnutChartPanel extends JPanel {
        private final Map<String, Double> data = new LinkedHashMap<>();
        private double totalUsage = 0.0;
        private final Color[] sliceColors = {
                new Color(47, 129, 247),  // #2f81f7
                new Color(35, 134, 54),   // #238636
                new Color(210, 153, 34),  // #d29922
                new Color(63, 185, 80),   // #3fb950
                new Color(163, 113, 247), // #a371f7
                new Color(218, 54, 51),   // #da3633
                new Color(240, 136, 62),  // #f0883e
                new Color(88, 166, 255),  // #58a6ff
                new Color(86, 211, 100),  // #56d364
                new Color(219, 97, 162)   // #db61a2
        };

        public DoughnutChartPanel() {
            setOpaque(false);
        }

        public void setUsageBreakdown(Map<String, Double> breakdown, double total) {
            data.clear();
            if (breakdown != null) data.putAll(breakdown);
            this.totalUsage = total > 0 ? total : data.values().stream().mapToDouble(Double::doubleValue).sum();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (data.isEmpty() || totalUsage <= 0.0) {
                g2.setColor(TEXT_MUTED);
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                String msg = "No traffic recorded yet. Connect a client to visualize share.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
                g2.dispose();
                return;
            }

            int chartDiameter = Math.min(w / 2, h - 30);
            int chartX = 18;
            int chartY = (h - chartDiameter) / 2;

            // Draw Doughnut slices
            double currentAngle = 90.0;
            int colorIdx = 0;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                double sliceAngle = (entry.getValue() / totalUsage) * 360.0;
                Color col = sliceColors[colorIdx % sliceColors.length];
                g2.setColor(col);
                g2.fillArc(chartX, chartY, chartDiameter, chartDiameter, (int) Math.round(currentAngle), (int) Math.ceil(sliceAngle));
                currentAngle += sliceAngle;
                colorIdx++;
            }

            // Cutout Inner Doughnut hole
            int innerDiameter = (int) (chartDiameter * 0.58);
            int innerX = chartX + (chartDiameter - innerDiameter) / 2;
            int innerY = chartY + (chartDiameter - innerDiameter) / 2;
            g2.setColor(BG_CARD);
            g2.fillOval(innerX, innerY, innerDiameter, innerDiameter);

            // Center Text inside Doughnut
            g2.setColor(TEXT_PRIMARY);
            g2.setFont(new Font("Consolas", Font.BOLD, 14));
            String totalStr = String.format("%.1f GB", totalUsage);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(totalStr, innerX + (innerDiameter - fm.stringWidth(totalStr)) / 2, innerY + innerDiameter / 2 + 5);

            // Draw Right Legend
            int legendX = chartX + chartDiameter + 24;
            int legendY = 24;
            colorIdx = 0;

            for (Map.Entry<String, Double> entry : data.entrySet()) {
                if (legendY > h - 16) break;
                Color col = sliceColors[colorIdx % sliceColors.length];
                double pct = (entry.getValue() / totalUsage) * 100.0;

                g2.setColor(col);
                g2.fillRoundRect(legendX, legendY - 9, 10, 10, 3, 3);

                g2.setColor(TEXT_PRIMARY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                String name = entry.getKey();
                if (name.length() > 14) name = name.substring(0, 12) + "..";
                g2.drawString(name, legendX + 16, legendY);

                g2.setColor(TEXT_SECONDARY);
                g2.setFont(new Font("Consolas", Font.PLAIN, 11));
                String stats = String.format("%.2f GB (%.0f%%)", entry.getValue(), pct);
                g2.drawString(stats, legendX + 130, legendY);

                legendY += 22;
                colorIdx++;
            }

            g2.dispose();
        }
    }

    // 2. Real-Time Speed Line Chart Panel
    public static class SpeedChartPanel extends JPanel {
        private final List<Double> dlSeries = new ArrayList<>();
        private final List<Double> ulSeries = new ArrayList<>();
        private final int maxPoints = 40;

        public SpeedChartPanel() {
            setOpaque(false);
            for (int i = 0; i < maxPoints; i++) {
                dlSeries.add(0.0);
                ulSeries.add(0.0);
            }
        }

        public synchronized void addDataPoint(double dlMbps, double ulMbps) {
            dlSeries.remove(0);
            dlSeries.add(dlMbps);
            ulSeries.remove(0);
            ulSeries.add(ulMbps);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padX = 24;
            int padY = 20;

            double maxVal = 5.0;
            synchronized (this) {
                for (double d : dlSeries) if (d > maxVal) maxVal = d;
                for (double u : ulSeries) if (u > maxVal) maxVal = u;
            }
            maxVal = Math.ceil(maxVal / 5.0) * 5.0;

            // Subtle Gridlines
            g2.setColor(BORDER_SUBTLE);
            int gridSteps = 4;
            for (int i = 0; i <= gridSteps; i++) {
                int gy = padY + i * (h - 2 * padY) / gridSteps;
                g2.drawLine(padX, gy, w - padX, gy);
            }

            int chartW = w - 2 * padX;
            int chartH = h - 2 * padY;
            double stepX = (double) chartW / (maxPoints - 1);

            int[] xPts = new int[maxPoints];
            int[] dlYPts = new int[maxPoints];
            int[] ulYPts = new int[maxPoints];

            synchronized (this) {
                for (int i = 0; i < maxPoints; i++) {
                    xPts[i] = padX + (int) (i * stepX);
                    dlYPts[i] = (h - padY) - (int) ((dlSeries.get(i) / maxVal) * chartH);
                    ulYPts[i] = (h - padY) - (int) ((ulSeries.get(i) / maxVal) * chartH);
                }
            }

            // Draw Download Filled Area (Green)
            Polygon dlPoly = new Polygon();
            dlPoly.addPoint(xPts[0], h - padY);
            for (int i = 0; i < maxPoints; i++) dlPoly.addPoint(xPts[i], dlYPts[i]);
            dlPoly.addPoint(xPts[maxPoints - 1], h - padY);
            g2.setColor(new Color(63, 185, 80, 20));
            g2.fillPolygon(dlPoly);

            // Draw Download Line (Neon Green)
            g2.setColor(new Color(63, 185, 80));
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < maxPoints - 1; i++) {
                g2.drawLine(xPts[i], dlYPts[i], xPts[i + 1], dlYPts[i + 1]);
            }

            // Draw Upload Line (Neon Blue)
            g2.setColor(new Color(88, 166, 255));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < maxPoints - 1; i++) {
                g2.drawLine(xPts[i], ulYPts[i], xPts[i + 1], ulYPts[i + 1]);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // 3. ENHANCED HISTORY BAR CHART PANEL (14-Day, Weekly, Monthly with Tooltips & GB Axis)
    // =========================================================================
    public static class HistoryBarChartPanel extends JPanel {
        private List<Map<String, Object>> records = new ArrayList<>();
        private String mode = "daily";
        private int hoverIndex = -1;

        public HistoryBarChartPanel() {
            setOpaque(false);
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int count = Math.min(14, records.size());
                    if (count == 0) return;
                    int padL = 50;
                    int padR = 24;
                    int padT = 24;
                    int padB = 28;
                    int w = getWidth();
                    int h = getHeight();
                    int chartW = w - padL - padR;
                    int groupW = chartW / count;

                    if (e.getX() >= padL && e.getX() <= padL + count * groupW && e.getY() >= padT && e.getY() <= h - padB) {
                        int idx = (e.getX() - padL) / groupW;
                        if (idx >= 0 && idx < count && idx != hoverIndex) {
                            hoverIndex = idx;
                            repaint();
                        }
                    } else if (hoverIndex != -1) {
                        hoverIndex = -1;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverIndex != -1) {
                        hoverIndex = -1;
                        repaint();
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public void setData(List<Map<String, Object>> data, String mode) {
            this.records = data != null ? data : new ArrayList<>();
            this.mode = mode;
            this.hoverIndex = -1;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padL = 50;
            int padR = 24;
            int padB = 28;
            int padT = 24;

            if (records.isEmpty()) {
                g2.setColor(TEXT_MUTED);
                g2.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                g2.drawString("No historical records available for this period.", w / 2 - 110, h / 2);
                g2.dispose();
                return;
            }

            int chartW = w - padL - padR;
            int chartH = h - padT - padB;

            double maxVal = 1.0;
            for (Map<String, Object> r : records) {
                double dl = ((Number) r.getOrDefault("download", 0.0)).doubleValue();
                double ul = ((Number) r.getOrDefault("upload", 0.0)).doubleValue();
                if (dl > maxVal) maxVal = dl;
                if (ul > maxVal) maxVal = ul;
            }
            maxVal = Math.ceil(maxVal * 1.15);
            if (maxVal < 1.0) maxVal = 1.0;

            // Draw Chart Legend in top right
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            int legW2 = 86;
            int legW1 = 100;
            int legX2 = w - padR - legW2;
            int legX1 = legX2 - legW1 - 12;
            int legY = 14;

            g2.setColor(ACCENT_GREEN_LIGHT);
            g2.fillRoundRect(legX1, legY - 8, 8, 8, 2, 2);
            g2.setColor(TEXT_SECONDARY);
            g2.drawString("Download (Rx)", legX1 + 12, legY);

            g2.setColor(ACCENT_BLUE);
            g2.fillRoundRect(legX2, legY - 8, 8, 8, 2, 2);
            g2.setColor(TEXT_SECONDARY);
            g2.drawString("Upload (Tx)", legX2 + 12, legY);

            // Horizontal Grid Lines & Y-Axis Scale (0 GB, 33%, 66%, 100%)
            g2.setFont(new Font("Consolas", Font.PLAIN, 10));
            int steps = 4;
            for (int i = 0; i <= steps; i++) {
                double frac = (double) i / steps;
                int yPos = (h - padB) - (int) (frac * chartH);
                double valAtGrid = frac * maxVal;

                g2.setColor(BORDER_SUBTLE);
                g2.drawLine(padL, yPos, w - padR, yPos);

                g2.setColor(TEXT_MUTED);
                String yLabel = String.format("%.1f GB", valAtGrid);
                g2.drawString(yLabel, 6, yPos + 4);
            }

            // Draw Dual Grouped Bars
            int count = Math.min(14, records.size());
            int groupW = chartW / count;
            int barW = Math.max(6, Math.min(24, (groupW / 2) - 4));

            int hoverX = -1, hoverY = -1;
            Map<String, Object> hoverRecord = null;

            for (int i = 0; i < count; i++) {
                Map<String, Object> r = records.get(i);
                double dl = ((Number) r.getOrDefault("download", 0.0)).doubleValue();
                double ul = ((Number) r.getOrDefault("upload", 0.0)).doubleValue();
                String dateLabel = String.valueOf(r.getOrDefault("date", ""));

                int gx = padL + i * groupW + (groupW - (barW * 2 + 2)) / 2;
                int dlH = Math.max(2, (int) ((dl / maxVal) * chartH));
                int ulH = Math.max(2, (int) ((ul / maxVal) * chartH));

                // If hovered, draw subtle column glow
                if (i == hoverIndex) {
                    g2.setColor(new Color(56, 139, 253, 20));
                    g2.fillRoundRect(padL + i * groupW, padT, groupW, chartH, 6, 6);
                    hoverX = gx;
                    hoverY = (h - padB) - Math.max(dlH, ulH);
                    hoverRecord = r;
                }

                // Download bar (Vibrant Emerald Green)
                g2.setColor(new Color(63, 185, 80, i == hoverIndex ? 255 : 210));
                g2.fillRoundRect(gx, (h - padB) - dlH, barW, dlH, 4, 4);

                // Upload bar (Vibrant Sky Blue)
                g2.setColor(new Color(88, 166, 255, i == hoverIndex ? 255 : 210));
                g2.fillRoundRect(gx + barW + 2, (h - padB) - ulH, barW, ulH, 4, 4);

                // Date Label
                g2.setColor(i == hoverIndex ? TEXT_PRIMARY : TEXT_MUTED);
                g2.setFont(new Font("Consolas", i == hoverIndex ? Font.BOLD : Font.PLAIN, 9));
                FontMetrics fm = g2.getFontMetrics();
                int tx = gx + (barW * 2 + 2 - fm.stringWidth(dateLabel)) / 2;
                g2.drawString(dateLabel, tx, h - 8);
            }

            // Draw Floating Tooltip Card if hovering over a group
            if (hoverRecord != null && hoverIndex >= 0) {
                String dTitle = String.valueOf(hoverRecord.getOrDefault("date", ""));
                double dl = ((Number) hoverRecord.getOrDefault("download", 0.0)).doubleValue();
                double ul = ((Number) hoverRecord.getOrDefault("upload", 0.0)).doubleValue();
                double tot = dl + ul;

                int ttW = 160;
                int ttH = 82;
                int ttX = Math.min(w - ttW - 12, Math.max(padL, hoverX - ttW / 2 + barW));
                int ttY = Math.max(padT + 4, hoverY - ttH - 8);

                // Tooltip background card
                g2.setColor(new Color(21, 26, 33, 245));
                g2.fillRoundRect(ttX, ttY, ttW, ttH, 8, 8);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(ttX, ttY, ttW, ttH, 8, 8);

                // Header / Date
                g2.setColor(TEXT_PRIMARY);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString(dTitle, ttX + 10, ttY + 18);

                // Download row
                g2.setColor(ACCENT_GREEN_LIGHT);
                g2.setFont(new Font("Consolas", Font.BOLD, 10));
                g2.drawString(String.format("↓ Rx: %.2f GB", dl), ttX + 10, ttY + 36);

                // Upload row
                g2.setColor(ACCENT_BLUE);
                g2.drawString(String.format("↑ Tx: %.2f GB", ul), ttX + 10, ttY + 52);

                // Total row
                g2.setColor(TEXT_PRIMARY);
                g2.drawString(String.format("Total: %.2f GB", tot), ttX + 10, ttY + 68);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // CUSTOM TABLE RENDERERS & ANALYTICS PANELS FOR TAB 2
    // =========================================================================

    public static class DeviceCellRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout(8, 0));
        private final JLabel lblIcon = new JLabel();
        private final JLabel lblName = new JLabel();
        private final JLabel lblSub = new JLabel();

        public DeviceCellRenderer() {
            panel.setOpaque(true);
            panel.setBorder(new EmptyBorder(4, 10, 4, 10));

            JPanel textStack = new JPanel();
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            textStack.setOpaque(false);

            lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblName.setForeground(TEXT_PRIMARY);

            lblSub.setFont(new Font("Consolas", Font.PLAIN, 10));
            lblSub.setForeground(TEXT_SECONDARY);

            textStack.add(lblName);
            textStack.add(Box.createRigidArea(new Dimension(0, 2)));
            textStack.add(lblSub);

            panel.add(lblIcon, BorderLayout.WEST);
            panel.add(textStack, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            panel.setBackground(isSelected ? new Color(33, 42, 56) : (row % 2 == 0 ? BG_CARD : BG_CARD_SUBTLE));
            if (value instanceof Device) {
                Device d = (Device) value;
                String typeKey = d.getDeviceType() != null ? d.getDeviceType().toLowerCase() : "laptop";
                lblIcon.setIcon(new NavIcon(typeKey, 18, ACCENT_BLUE));
                lblName.setText(d.getDeviceName());
                lblSub.setText((d.getOwnerName() != null ? d.getOwnerName() : "Client") + " • " + (d.getIpAddress() != null ? d.getIpAddress() : ""));
            } else {
                lblName.setText(String.valueOf(value));
                lblSub.setText("");
                lblIcon.setIcon(new NavIcon("laptop", 18, ACCENT_BLUE));
            }
            return panel;
        }
    }

    public static class UsageLimitRenderer implements javax.swing.table.TableCellRenderer {
        private final JPanel panel = new JPanel();
        private final JLabel lblRatio = new JLabel();
        private final JLabel lblPct = new JLabel();
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final JLabel lblSub = new JLabel();

        public UsageLimitRenderer() {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(true);
            panel.setBorder(new EmptyBorder(4, 10, 4, 10));

            JPanel topRow = new JPanel(new BorderLayout());
            topRow.setOpaque(false);
            lblRatio.setFont(new Font("Consolas", Font.BOLD, 11));
            lblRatio.setForeground(TEXT_PRIMARY);
            lblPct.setFont(new Font("Consolas", Font.BOLD, 11));
            topRow.add(lblRatio, BorderLayout.WEST);
            topRow.add(lblPct, BorderLayout.EAST);

            progressBar.setPreferredSize(new Dimension(100, 6));
            progressBar.setMaximumSize(new Dimension(3000, 6));
            progressBar.setStringPainted(false);
            progressBar.setBackground(new Color(28, 36, 48));
            progressBar.setBorder(BorderFactory.createEmptyBorder());

            lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            panel.add(topRow);
            panel.add(Box.createRigidArea(new Dimension(0, 3)));
            panel.add(progressBar);
            panel.add(Box.createRigidArea(new Dimension(0, 2)));
            panel.add(lblSub);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            panel.setBackground(isSelected ? new Color(33, 42, 56) : (row % 2 == 0 ? BG_CARD : BG_CARD_SUBTLE));
            if (value instanceof Device) {
                Device d = (Device) value;
                double tot = d.getDownloadGb() + d.getUploadGb();
                double limit = d.getDataLimit();
                double pct = limit > 0 ? (tot / limit) * 100.0 : 0.0;
                double rem = Math.max(0, limit - tot);
                boolean isExceeded = tot >= limit && limit > 0;
                boolean isNear = !isExceeded && pct >= 80.0;

                lblRatio.setText(String.format("%.2f GB / %.1f GB", tot, limit));
                lblPct.setText(String.format("%.1f%%", pct));
                progressBar.setValue((int) Math.min(100, Math.round(pct)));

                Color barCol;
                if (d.isBlocked() || isExceeded) {
                    barCol = ACCENT_RED_LIGHT;
                    lblPct.setForeground(ACCENT_RED_LIGHT);
                    lblSub.setForeground(ACCENT_RED_LIGHT);
                    lblSub.setText(d.isBlocked() ? "Device blocked" : String.format("Exceeded by %.2f GB", tot - limit));
                } else if (isNear) {
                    barCol = ACCENT_AMBER;
                    lblPct.setForeground(ACCENT_AMBER);
                    lblSub.setForeground(ACCENT_AMBER);
                    lblSub.setText(String.format("%.2f GB remaining (Near Limit)", rem));
                } else {
                    barCol = ACCENT_GREEN_LIGHT;
                    lblPct.setForeground(ACCENT_GREEN_LIGHT);
                    lblSub.setForeground(TEXT_MUTED);
                    lblSub.setText(String.format("%.2f GB remaining", rem));
                }

                final Color activeBarCol = barCol;
                progressBar.setUI(new javax.swing.plaf.basic.BasicProgressBarUI() {
                    @Override
                    protected void paintDeterminate(Graphics g, JComponent c) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int w = c.getWidth();
                        int h = c.getHeight();
                        g2.setColor(new Color(28, 36, 48));
                        g2.fillRoundRect(0, 0, w, h, 4, 4);
                        int amount = getAmountFull(new Insets(0, 0, 0, 0), w, h);
                        if (amount > 0) {
                            g2.setColor(activeBarCol);
                            g2.fillRoundRect(0, 0, amount, h, 4, 4);
                        }
                        g2.dispose();
                    }
                });
            }
            return panel;
        }
    }

    public static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            l.setOpaque(true);
            l.setBackground(isSelected ? new Color(33, 42, 56) : (row % 2 == 0 ? BG_CARD : BG_CARD_SUBTLE));
            l.setBorder(new EmptyBorder(0, 10, 0, 10));
            l.setFont(new Font("Segoe UI", Font.BOLD, 11));

            String st = String.valueOf(value);
            if ("Normal".equalsIgnoreCase(st)) {
                l.setText("● Normal");
                l.setForeground(ACCENT_GREEN_LIGHT);
            } else if ("Near Limit".equalsIgnoreCase(st)) {
                l.setText("● Near Limit");
                l.setForeground(ACCENT_AMBER);
            } else if ("Limit Exceeded".equalsIgnoreCase(st)) {
                l.setText("● Limit Exceeded");
                l.setForeground(ACCENT_RED_LIGHT);
            } else if ("Blocked".equalsIgnoreCase(st)) {
                l.setText("● Blocked");
                l.setForeground(ACCENT_RED_LIGHT);
            } else {
                l.setText("● " + st);
                l.setForeground(TEXT_SECONDARY);
            }
            return l;
        }
    }

    public static class TopConsumersPanel extends JPanel {
        private List<Device> devices = new ArrayList<>();

        public TopConsumersPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        public void updateData(List<Device> list) {
            this.devices = list != null ? new ArrayList<>(list) : new ArrayList<>();
            this.devices.sort((a, b) -> Double.compare(b.getDownloadGb() + b.getUploadGb(), a.getDownloadGb() + a.getUploadGb()));

            removeAll();
            if (devices.isEmpty()) {
                JLabel lblEmpty = new JLabel("No client devices registered.");
                lblEmpty.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                lblEmpty.setForeground(TEXT_MUTED);
                add(lblEmpty);
            } else {
                int count = Math.min(3, devices.size());
                double maxUsage = 0.01;
                for (Device d : devices) {
                    double u = d.getDownloadGb() + d.getUploadGb();
                    if (u > maxUsage) maxUsage = u;
                }

                for (int i = 0; i < count; i++) {
                    Device d = devices.get(i);
                    double u = d.getDownloadGb() + d.getUploadGb();
                    double barFraction = Math.max(0.05, Math.min(1.0, u / maxUsage));

                    JPanel item = new JPanel();
                    item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                    item.setOpaque(false);
                    item.setBorder(new EmptyBorder(2, 0, 4, 0));

                    JPanel row = new JPanel(new BorderLayout());
                    row.setOpaque(false);

                    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                    left.setOpaque(false);

                    JLabel lblRank = new JLabel((i + 1) + ".");
                    lblRank.setFont(new Font("Consolas", Font.BOLD, 11));
                    lblRank.setForeground(TEXT_MUTED);

                    String typeKey = d.getDeviceType() != null ? d.getDeviceType().toLowerCase() : "laptop";
                    JLabel lblIcon = new JLabel(new NavIcon(typeKey, 14, ACCENT_BLUE));

                    JLabel lblName = new JLabel(d.getDeviceName());
                    lblName.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    lblName.setForeground(TEXT_PRIMARY);

                    left.add(lblRank);
                    left.add(lblIcon);
                    left.add(lblName);

                    JLabel lblGb = new JLabel(String.format("%.2f GB", u));
                    lblGb.setFont(new Font("Consolas", Font.BOLD, 11));
                    lblGb.setForeground(ACCENT_BLUE);

                    row.add(left, BorderLayout.WEST);
                    row.add(lblGb, BorderLayout.EAST);

                    final double bf = barFraction;
                    JPanel bar = new JPanel() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            super.paintComponent(g);
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int w = getWidth();
                            int h = getHeight();
                            g2.setColor(new Color(28, 36, 48));
                            g2.fillRoundRect(0, 0, w, h, 3, 3);
                            int fw = (int) (w * bf);
                            if (fw > 0) {
                                g2.setColor(ACCENT_BLUE);
                                g2.fillRoundRect(0, 0, fw, h, 3, 3);
                            }
                            g2.dispose();
                        }
                    };
                    bar.setPreferredSize(new Dimension(100, 5));
                    bar.setMaximumSize(new Dimension(3000, 5));
                    bar.setOpaque(false);

                    item.add(row);
                    item.add(Box.createRigidArea(new Dimension(0, 3)));
                    item.add(bar);
                    add(item);
                    if (i < count - 1) {
                        add(Box.createRigidArea(new Dimension(0, 4)));
                    }
                }
            }
            revalidate();
            repaint();
        }
    }

    public static class NetworkSummaryPanel extends JPanel {
        private final JLabel lblDl = new JLabel("0.00 GB");
        private final JLabel lblUl = new JLabel("0.00 GB");
        private final JLabel lblTot = new JLabel("0.00 GB");
        private final JLabel lblDevs = new JLabel("0 / 0 Devices");
        private double rxFraction = 0.5;

        public NetworkSummaryPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            add(createRow("Total Download", lblDl, ACCENT_GREEN_LIGHT));
            add(Box.createRigidArea(new Dimension(0, 4)));
            add(createRow("Total Upload", lblUl, ACCENT_CYAN));
            add(Box.createRigidArea(new Dimension(0, 4)));
            add(createRow("Total Network Usage", lblTot, TEXT_PRIMARY));
            add(Box.createRigidArea(new Dimension(0, 4)));
            add(createRow("Connected Devices", lblDevs, ACCENT_PURPLE));
            add(Box.createRigidArea(new Dimension(0, 6)));

            // Dual Ratio Split Bar
            JPanel ratioBar = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    g2.setColor(new Color(28, 36, 48));
                    g2.fillRoundRect(0, 0, w, h, 4, 4);
                    int rw = (int) (w * rxFraction);
                    if (rw > 0) {
                        g2.setColor(ACCENT_GREEN_LIGHT);
                        g2.fillRoundRect(0, 0, rw, h, 4, 4);
                    }
                    if (w - rw > 0) {
                        g2.setColor(ACCENT_BLUE);
                        g2.fillRoundRect(rw, 0, w - rw, h, 4, 4);
                    }
                    g2.dispose();
                }
            };
            ratioBar.setPreferredSize(new Dimension(100, 6));
            ratioBar.setMaximumSize(new Dimension(3000, 6));
            ratioBar.setOpaque(false);
            add(ratioBar);
        }

        private JPanel createRow(String title, JLabel val, Color c) {
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);
            JLabel t = new JLabel(title);
            t.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            t.setForeground(TEXT_SECONDARY);
            val.setFont(new Font("Consolas", Font.BOLD, 11));
            val.setForeground(c);
            p.add(t, BorderLayout.WEST);
            p.add(val, BorderLayout.EAST);
            return p;
        }

        public void updateMetrics(double dl, double ul, double tot, int connected, int totalDev) {
            lblDl.setText(String.format("%.2f GB", dl));
            lblUl.setText(String.format("%.2f GB", ul));
            lblTot.setText(String.format("%.2f GB", tot));
            lblDevs.setText(connected + " / " + totalDev + " Devices");
            rxFraction = tot > 0 ? Math.max(0.05, Math.min(0.95, dl / tot)) : 0.5;
            revalidate();
            repaint();
        }
    }

    // =========================================================================
    // 4. Custom Anti-Aliased Vector Nav Icons (No emoji or missing glyphs)
    // =========================================================================
    public static class NavIcon implements Icon {
        private final String type;
        private final int size;
        private final Color customColor;

        public NavIcon(String type) {
            this(type, 18, null);
        }

        public NavIcon(String type, int size) {
            this(type, size, null);
        }

        public NavIcon(String type, int size, Color customColor) {
            this.type = type;
            this.size = size;
            this.customColor = customColor;
        }

        @Override
        public int getIconWidth() { return size; }

        @Override
        public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color col = customColor != null ? customColor : (c != null ? c.getForeground() : TEXT_SECONDARY);
            g2.setColor(col);

            int w = size;
            int h = size;

            switch (type) {
                case "dashboard":
                    int sq = (w - 3) / 2;
                    g2.fillRoundRect(x, y, sq, sq, 3, 3);
                    g2.fillRoundRect(x + sq + 3, y, sq, sq, 3, 3);
                    g2.fillRoundRect(x, y + sq + 3, sq, sq, 3, 3);
                    g2.fillRoundRect(x + sq + 3, y + sq + 3, sq, sq, 3, 3);
                    break;
                case "history":
                    int bw = (w - 6) / 3;
                    g2.fillRect(x, y + h - 6, bw, 6);
                    g2.fillRect(x + bw + 3, y + h - 14, bw, 14);
                    g2.fillRect(x + (bw + 3) * 2, y + h - 10, bw, 10);
                    g2.drawLine(x, y + h - 1, x + w, y + h - 1);
                    break;
                case "devices":
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 2, y + 2, w - 4, h - 8, 3, 3);
                    g2.drawLine(x + 1, y + h - 3, x + w - 1, y + h - 3);
                    g2.fillRect(x + (w / 2) - 2, y + h - 3, 4, 2);
                    break;
                case "admin":
                    g2.setStroke(new BasicStroke(1.5f));
                    Polygon shield = new Polygon();
                    shield.addPoint(x + w / 2, y + 1);
                    shield.addPoint(x + w - 2, y + 4);
                    shield.addPoint(x + w - 2, y + h / 2);
                    shield.addPoint(x + w / 2, y + h - 1);
                    shield.addPoint(x + 2, y + h / 2);
                    shield.addPoint(x + 2, y + 4);
                    g2.drawPolygon(shield);
                    g2.fillOval(x + w / 2 - 2, y + h / 2 - 2, 4, 4);
                    break;
                case "blocking":
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawOval(x + 1, y + 1, w - 2, h - 2);
                    g2.drawLine(x + 4, y + 4, x + w - 4, y + h - 4);
                    break;
                case "ai":
                    g2.setStroke(new BasicStroke(1.4f));
                    int r = 3;
                    int cx1 = x + 4, cy1 = y + h / 2;
                    int cx2 = x + w - 5, cy2 = y + 4;
                    int cx3 = x + w - 5, cy3 = y + h - 5;
                    g2.drawLine(cx1, cy1, cx2, cy2);
                    g2.drawLine(cx1, cy1, cx3, cy3);
                    g2.drawLine(cx2, cy2, cx3, cy3);
                    g2.fillOval(cx1 - r, cy1 - r, r * 2, r * 2);
                    g2.fillOval(cx2 - r, cy2 - r, r * 2, r * 2);
                    g2.fillOval(cx3 - r, cy3 - r, r * 2, r * 2);
                    break;
                case "wifi":
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawArc(x + 1, y + 2, w - 2, h + 2, 45, 90);
                    g2.drawArc(x + 4, y + 6, w - 8, h - 3, 45, 90);
                    g2.fillOval(x + w / 2 - 2, y + h - 4, 4, 4);
                    break;
                case "sync":
                case "refresh":
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int pad = 1;
                    int d = w - pad * 2;
                    int ox = x + pad;
                    int oy = y + pad;
                    // Top arc
                    g2.drawArc(ox, oy, d, d, 25, 130);
                    int x1 = ox + 1;
                    int y1 = oy + d / 3;
                    g2.drawLine(x1, y1, x1, y1 - 3);
                    g2.drawLine(x1, y1, x1 + 3, y1);
                    // Bottom arc
                    g2.drawArc(ox, oy, d, d, 205, 130);
                    int x2 = ox + d - 1;
                    int y2 = oy + (2 * d) / 3;
                    g2.drawLine(x2, y2, x2, y2 + 3);
                    g2.drawLine(x2, y2, x2 - 3, y2);
                    break;
                case "laptop":
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 2, y + 2, w - 4, h - 8, 2, 2);
                    g2.drawLine(x, y + h - 3, x + w, y + h - 3);
                    break;
                case "mobile":
                case "phone":
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 3, y + 1, w - 6, h - 2, 3, 3);
                    g2.fillOval(x + w / 2 - 1, y + h - 4, 2, 2);
                    break;
                case "tv":
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 1, y + 2, w - 2, h - 7, 2, 2);
                    g2.drawLine(x + w / 2 - 3, y + h - 2, x + w / 2 + 3, y + h - 2);
                    g2.drawLine(x + w / 2, y + h - 5, x + w / 2, y + h - 2);
                    break;
                case "desktop":
                case "pc":
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 1, y + 1, w - 2, h - 6, 2, 2);
                    g2.drawLine(x + w / 2 - 4, y + h - 1, x + w / 2 + 4, y + h - 1);
                    g2.drawLine(x + w / 2, y + h - 5, x + w / 2, y + h - 1);
                    break;
                case "iot":
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 3, 3);
                    g2.drawOval(x + w / 2 - 2, y + h / 2 - 2, 4, 4);
                    break;
                default:
                    g2.fillOval(x + 2, y + 2, w - 4, h - 4);
                    break;
            }
            g2.dispose();
        }
    }
}
