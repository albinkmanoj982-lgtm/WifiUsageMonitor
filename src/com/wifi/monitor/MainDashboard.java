package com.wifi.monitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;

public class MainDashboard extends JFrame {

    private final String currentUsername;
    private final DeviceDAO deviceDAO = new DeviceDAO();
    private final UsageDAO usageDAO = new UsageDAO();
    private final ReportGenerator reportGenerator = new ReportGenerator();

    // Accent Colors
    private final Color COLOR_ACCENT_BLUE = new Color(10, 132, 255);
    private final Color COLOR_ACCENT_GREEN = new Color(48, 209, 88);
    private final Color COLOR_ACCENT_RED = new Color(255, 69, 58);

    // UI Components to update dynamically
    private JLabel lblTotalDevicesVal;
    private JLabel lblTotalUsageVal;
    private JLabel lblActiveAlertsVal;
    private JTextArea txtAlertList;

    private JTable tblDevices;
    private DefaultTableModel modelDevices;
    private JComboBox<String> cmbDeviceSelect;

    private JTable tblUsage;
    private DefaultTableModel modelUsage;

    private JTextArea txtReportArea;
    private JTextField txtReportDate;

    // Speed Meter Components
    private SpeedChartPanel speedChart;
    private JLabel lblCurrentDownload;
    private JLabel lblCurrentUpload;
    private JLabel lblCurrentPing;
    private long lastReceivedBytes = 0;
    private long lastSentBytes = 0;
    private long lastSpeedTimestamp = 0;

    // Real-Time usage tracker variables
    private int hostDeviceId = -1;
    private double accumulatedGb = 0.0;
    private int loopCounter = 0;

    // Navigation and Card Layout
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private JPanel sidebarButtonPanel;

    public MainDashboard(String username) {
        this.currentUsername = username;

        setTitle("WiFi Usage Monitor - Dashboard");
        setSize(1000, 680);
        setMinimumSize(new Dimension(920, 620));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Multi-color Gradient background panel
        JPanel mainLayout = new LoginSignupUI.GradientBackgroundPanel();
        mainLayout.setLayout(new BorderLayout());

        // Sidebar
        mainLayout.add(createSidebarPanel(), BorderLayout.WEST);

        // Content cards (make transparent to let main gradient shine through)
        contentPanel.setOpaque(false);
        contentPanel.add(createHomePanel(), "home");
        contentPanel.add(createDevicesPanel(), "devices");
        contentPanel.add(createUsagePanel(), "usage");
        contentPanel.add(createReportsPanel(), "reports");
        contentPanel.add(createSpeedMeterPanel(), "speed");

        mainLayout.add(contentPanel, BorderLayout.CENTER);
        add(mainLayout);

        // Auto-Register/Lookup host computer
        initHostDevice();

        // Initial Data Refresh
        refreshAllData();

        // Start Real-Time Bandwidth speed meter worker thread
        startSpeedMeter();

        // Swing Timer to refresh UI data every 5 seconds
        new javax.swing.Timer(5000, e -> refreshAllData()).start();

        setVisible(true);
    }

    private JPanel createSidebarPanel() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(220, 680));
        sidebar.setOpaque(false);

        // Glass background with border
        JPanel glassSidebar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 12)); // Frosted glass
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 45)); // Right border
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.dispose();
            }
        };
        glassSidebar.setOpaque(false);

        // Sidebar Header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(25, 20, 20, 20));

        JLabel lblLogo = new JLabel("📶 WiFi Monitor");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(lblLogo);

        headerPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel lblWelcome = new JLabel("User: " + currentUsername);
        lblWelcome.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblWelcome.setForeground(new Color(210, 210, 220));
        lblWelcome.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(lblWelcome);

        glassSidebar.add(headerPanel, BorderLayout.NORTH);

        // Sidebar Buttons Panel (Centered list)
        sidebarButtonPanel = new JPanel();
        sidebarButtonPanel.setLayout(new BoxLayout(sidebarButtonPanel, BoxLayout.Y_AXIS));
        sidebarButtonPanel.setOpaque(false);
        sidebarButtonPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JButton btnHome = createSidebarNavButton("🏠  Home Overview", "home");
        JButton btnDevices = createSidebarNavButton("🖥️  Devices", "devices");
        JButton btnUsage = createSidebarNavButton("📊  Usage Log", "usage");
        JButton btnReports = createSidebarNavButton("📋  Traffic Reports", "reports");
        JButton btnSpeed = createSidebarNavButton("⚡  Speed Meter", "speed");

        sidebarButtonPanel.add(btnHome);
        sidebarButtonPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarButtonPanel.add(btnDevices);
        sidebarButtonPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarButtonPanel.add(btnUsage);
        sidebarButtonPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarButtonPanel.add(btnReports);
        sidebarButtonPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebarButtonPanel.add(btnSpeed);

        glassSidebar.add(sidebarButtonPanel, BorderLayout.CENTER);

        // Sidebar Footer (Logout button)
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.setBorder(new EmptyBorder(15, 20, 25, 20));

        JButton btnLogout = new LoginSignupUI.GlassButton("Log Out", COLOR_ACCENT_RED, 40);
        btnLogout.setPreferredSize(new Dimension(180, 38));
        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                    "Are you sure you want to log out?", "Logout Confirmation", 
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                this.dispose();
                SwingUtilities.invokeLater(LoginSignupUI::new);
            }
        });

        footerPanel.add(btnLogout, BorderLayout.CENTER);
        glassSidebar.add(footerPanel, BorderLayout.SOUTH);

        sidebar.add(glassSidebar, BorderLayout.CENTER);
        setNavButtonActive(btnHome);

        return sidebar;
    }

    private JButton createSidebarNavButton(String text, String cardName) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setForeground(new Color(200, 200, 210));
        btn.setBackground(new Color(0, 0, 0, 0));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(190, 42));
        btn.setPreferredSize(new Dimension(190, 42));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.getBackground().getAlpha() == 0) {
                    btn.setBackground(new Color(255, 255, 255, 15));
                    btn.setForeground(Color.WHITE);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.getForeground() != Color.WHITE || btn.getBackground().getAlpha() == 15) {
                    btn.setBackground(new Color(0, 0, 0, 0));
                    btn.setForeground(new Color(200, 200, 210));
                }
            }
        });

        btn.addActionListener(e -> {
            cardLayout.show(contentPanel, cardName);
            setNavButtonActive(btn);
        });

        return btn;
    }

    private void setNavButtonActive(JButton activeBtn) {
        for (Component c : sidebarButtonPanel.getComponents()) {
            if (c instanceof JButton) {
                JButton b = (JButton) c;
                b.setBackground(new Color(0, 0, 0, 0));
                b.setForeground(new Color(200, 200, 210));
            }
        }
        activeBtn.setBackground(new Color(255, 255, 255, 45)); // Active glass highlight
        activeBtn.setForeground(Color.WHITE);
    }

    // ==========================================
    // CARD 1: HOME PANEL
    // ==========================================
    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header Panel
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Network Status Overview");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        
        JLabel subtitle = new JLabel("Real-time network usage and data limits overview");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(220, 220, 230));
        header.add(subtitle, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);

        // Stats Cards Grid
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 20, 0));
        statsPanel.setOpaque(false);
        statsPanel.setBorder(new EmptyBorder(30, 0, 30, 0));

        JPanel card1 = createStatCard("Total Devices", lblTotalDevicesVal = new JLabel("0"), COLOR_ACCENT_BLUE);
        JPanel card2 = createStatCard("Total Network Usage", lblTotalUsageVal = new JLabel("0.00 GB"), COLOR_ACCENT_GREEN);
        JPanel card3 = createStatCard("Active Alerts", lblActiveAlertsVal = new JLabel("0"), COLOR_ACCENT_RED);

        statsPanel.add(card1);
        statsPanel.add(card2);
        statsPanel.add(card3);

        // Alerts List Container
        JPanel alertsPanel = new LoginSignupUI.GlassCardPanel(15);
        alertsPanel.setLayout(new BorderLayout());
        alertsPanel.setBorder(BorderFactory.createCompoundBorder(
                alertsPanel.getBorder(),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel alertsTitle = new JLabel("⚠️  Usage Limit Warnings", SwingConstants.LEFT);
        alertsTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        alertsTitle.setForeground(COLOR_ACCENT_RED);
        alertsTitle.setBorder(new EmptyBorder(0, 0, 15, 0));
        alertsPanel.add(alertsTitle, BorderLayout.NORTH);

        txtAlertList = new JTextArea();
        txtAlertList.setEditable(false);
        txtAlertList.setOpaque(false);
        txtAlertList.setBackground(new Color(0, 0, 0, 0));
        txtAlertList.setForeground(Color.WHITE);
        txtAlertList.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtAlertList.setMargin(new Insets(12, 15, 12, 15));
        txtAlertList.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 40), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JScrollPane scrollAlerts = new JScrollPane(txtAlertList);
        scrollAlerts.setOpaque(false);
        scrollAlerts.getViewport().setOpaque(false);
        scrollAlerts.setBorder(null);
        alertsPanel.add(scrollAlerts, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(statsPanel, BorderLayout.NORTH);
        centerPanel.add(alertsPanel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new LoginSignupUI.GlassCardPanel(15) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentColor);
                // Draw left colored bar
                g2.fillRoundRect(0, 0, 5, getHeight(), 15, 15);
                g2.fillRect(0, 0, 5, getHeight());
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                card.getBorder(),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(new Color(200, 200, 210));
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(lblTitle);
        card.add(Box.createRigidArea(new Dimension(0, 10)));
        card.add(valueLabel);

        return card;
    }

    // ==========================================
    // CARD 2: DEVICES PANEL
    // ==========================================
    private JPanel createDevicesPanel() {
        JPanel panel = new JPanel(new BorderLayout(25, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));
        
        JLabel title = new JLabel("Device Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        headerPanel.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Add, monitor and delete registered network devices");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(220, 220, 230));
        headerPanel.add(subtitle, BorderLayout.SOUTH);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Table
        String[] cols = {"Device ID", "Device Name", "Owner", "Device Type", "Data Limit (GB)"};
        modelDevices = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblDevices = new JTable(modelDevices);
        styleGlassTable(tblDevices);

        JScrollPane scrollTable = new JScrollPane(tblDevices);
        scrollTable.setOpaque(false);
        scrollTable.getViewport().setOpaque(false);
        scrollTable.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 45)));
        scrollTable.setBackground(new Color(0, 0, 0, 0));
        panel.add(scrollTable, BorderLayout.CENTER);

        // Registration Form Panel (Right Sidebar Glass Card)
        JPanel formPanel = new LoginSignupUI.GlassCardPanel(15);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setPreferredSize(new Dimension(300, 0));
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                formPanel.getBorder(),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel lblFormTitle = new JLabel("Register New Device");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFormTitle.setForeground(Color.WHITE);
        lblFormTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblFormTitle);
        formPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JButton btnAutoDetect = new LoginSignupUI.GlassButton("🔍 Auto-Detect Devices", COLOR_ACCENT_GREEN, 40);
        btnAutoDetect.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnAutoDetect.setMaximumSize(new Dimension(260, 36));
        formPanel.add(btnAutoDetect);
        formPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        JTextField txtDevName = createGlassFormFields(formPanel, "Device Name");
        JTextField txtDevOwner = createGlassFormFields(formPanel, "Owner Name");

        JLabel lblType = new JLabel("Device Type");
        lblType.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblType.setForeground(new Color(200, 200, 210));
        lblType.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblType);
        formPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        JComboBox<String> cmbType = new JComboBox<>(new String[]{"Mobile", "Laptop", "Desktop", "Smart TV", "Other"});
        cmbType.setBackground(new Color(255, 255, 255, 30));
        cmbType.setForeground(Color.WHITE);
        cmbType.setMaximumSize(new Dimension(260, 36));
        cmbType.setPreferredSize(new Dimension(260, 36));
        cmbType.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(cmbType);
        formPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        JTextField txtDevLimit = createGlassFormFields(formPanel, "Data Limit (GB)");

        JButton btnAdd = new LoginSignupUI.GlassButton("Add Device", COLOR_ACCENT_BLUE, 40);
        btnAdd.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnAdd.setMaximumSize(new Dimension(260, 40));
        formPanel.add(btnAdd);

        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(260, 2));
        sep.setForeground(new Color(255, 255, 255, 50));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(sep);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        JButton btnRemove = new LoginSignupUI.GlassButton("Delete Selected Device", COLOR_ACCENT_RED, 40);
        btnRemove.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRemove.setMaximumSize(new Dimension(260, 40));
        formPanel.add(btnRemove);

        // Actions
        btnAutoDetect.addActionListener(e -> showAutoDetectDialog(txtDevName, cmbType));
        btnAdd.addActionListener(e -> {
            String name = txtDevName.getText().trim();
            String owner = txtDevOwner.getText().trim();
            String type = (String) cmbType.getSelectedItem();
            String limitStr = txtDevLimit.getText().trim();

            if (name.isEmpty() || owner.isEmpty() || limitStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in all form fields.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                double limit = Double.parseDouble(limitStr);
                Device newDev = new Device(0, name, owner, type, limit);
                deviceDAO.addDevice(newDev);
                refreshAllData();

                // Clear
                txtDevName.setText("");
                txtDevOwner.setText("");
                txtDevLimit.setText("");
                JOptionPane.showMessageDialog(this, "Device registered successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid Data Limit. Must be a decimal number.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRemove.addActionListener(e -> {
            int selectedRow = tblDevices.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a device from the table to delete.", "Select Row", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int deviceId = (int) modelDevices.getValueAt(selectedRow, 0);
            String deviceName = (String) modelDevices.getValueAt(selectedRow, 1);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete '" + deviceName + "' and all its usage records?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                deviceDAO.removeDevice(deviceId);
                refreshAllData();
                JOptionPane.showMessageDialog(this, "Device and its records deleted.", "Deleted", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        panel.add(formPanel, BorderLayout.EAST);
        return panel;
    }

    // ==========================================
    // CARD 3: USAGE PANEL
    // ==========================================
    private JPanel createUsagePanel() {
        JPanel panel = new JPanel(new BorderLayout(25, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel title = new JLabel("Internet Usage Logger");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        headerPanel.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Log daily internet usage for active devices");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(220, 220, 230));
        headerPanel.add(subtitle, BorderLayout.SOUTH);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Table
        String[] cols = {"Record ID", "Device ID", "Device Info", "Date", "Data Used (GB)"};
        modelUsage = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblUsage = new JTable(modelUsage);
        styleGlassTable(tblUsage);

        JScrollPane scrollTable = new JScrollPane(tblUsage);
        scrollTable.setOpaque(false);
        scrollTable.getViewport().setOpaque(false);
        scrollTable.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 45)));
        scrollTable.setBackground(new Color(0, 0, 0, 0));
        panel.add(scrollTable, BorderLayout.CENTER);

        // Side logger form
        JPanel formPanel = new LoginSignupUI.GlassCardPanel(15);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setPreferredSize(new Dimension(300, 0));
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                formPanel.getBorder(),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel lblFormTitle = new JLabel("Log Internet Usage");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFormTitle.setForeground(Color.WHITE);
        lblFormTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblFormTitle);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel lblDeviceSelect = new JLabel("Select Device");
        lblDeviceSelect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDeviceSelect.setForeground(new Color(200, 200, 210));
        lblDeviceSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblDeviceSelect);
        formPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        cmbDeviceSelect = new JComboBox<>();
        cmbDeviceSelect.setBackground(new Color(255, 255, 255, 30));
        cmbDeviceSelect.setForeground(Color.WHITE);
        cmbDeviceSelect.setMaximumSize(new Dimension(260, 36));
        cmbDeviceSelect.setPreferredSize(new Dimension(260, 36));
        cmbDeviceSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(cmbDeviceSelect);
        formPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        JTextField txtDate = createGlassFormFields(formPanel, "Date (yyyy-MM-dd)");
        txtDate.setText(LocalDate.now().toString());

        JTextField txtDataUsed = createGlassFormFields(formPanel, "Data Used (GB)");

        JButton btnLog = new LoginSignupUI.GlassButton("Record Usage", COLOR_ACCENT_GREEN, 40);
        btnLog.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLog.setMaximumSize(new Dimension(260, 40));
        formPanel.add(btnLog);

        // Actions
        btnLog.addActionListener(e -> {
            String devItem = (String) cmbDeviceSelect.getSelectedItem();
            String date = txtDate.getText().trim();
            String dataUsedStr = txtDataUsed.getText().trim();

            if (devItem == null || devItem.contains("No devices")) {
                JOptionPane.showMessageDialog(this, "No valid device selected.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (date.isEmpty() || dataUsedStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in all form fields.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                int devId = Integer.parseInt(devItem.split("\\|")[0].replace("ID:", "").trim());
                double dataUsed = Double.parseDouble(dataUsedStr);

                usageDAO.addUsageRecord(devId, date, dataUsed);
                refreshAllData();

                txtDataUsed.setText("");
                JOptionPane.showMessageDialog(this, "Usage record added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid inputs. Make sure Data is decimal.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(formPanel, BorderLayout.EAST);
        return panel;
    }

    // ==========================================
    // CARD 4: REPORTS PANEL
    // ==========================================
    private JPanel createReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        
        JLabel title = new JLabel("Daily Traffic Reports");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        headerPanel.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Generate network consumption report for a selected date");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(220, 220, 230));
        headerPanel.add(subtitle, BorderLayout.SOUTH);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Control Panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        filterPanel.setOpaque(false);

        JLabel lblDatePrompt = new JLabel("Enter Date (yyyy-MM-dd): ");
        lblDatePrompt.setForeground(Color.WHITE);
        lblDatePrompt.setFont(new Font("Segoe UI", Font.BOLD, 13));
        filterPanel.add(lblDatePrompt);

        txtReportDate = new LoginSignupUI.GlassTextField();
        txtReportDate.setPreferredSize(new Dimension(140, 38));
        txtReportDate.setText(LocalDate.now().toString());
        filterPanel.add(txtReportDate);

        JButton btnGen = new LoginSignupUI.GlassButton("Generate Report", COLOR_ACCENT_BLUE, 40);
        btnGen.setPreferredSize(new Dimension(160, 38));
        filterPanel.add(btnGen);

        // Report Text Area
        txtReportArea = new JTextArea();
        txtReportArea.setEditable(false);
        txtReportArea.setOpaque(false);
        txtReportArea.setBackground(new Color(0, 0, 0, 0));
        txtReportArea.setForeground(Color.WHITE);
        txtReportArea.setFont(new Font("Consolas", Font.PLAIN, 15));
        txtReportArea.setMargin(new Insets(20, 20, 20, 20));
        txtReportArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 45), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JScrollPane scrollReport = new JScrollPane(txtReportArea);
        scrollReport.setOpaque(false);
        scrollReport.getViewport().setOpaque(false);
        scrollReport.setBorder(null);

        JPanel mainReportLayout = new JPanel(new BorderLayout(0, 15));
        mainReportLayout.setOpaque(false);
        mainReportLayout.add(filterPanel, BorderLayout.NORTH);
        mainReportLayout.add(scrollReport, BorderLayout.CENTER);

        panel.add(mainReportLayout, BorderLayout.CENTER);

        // Action
        btnGen.addActionListener(e -> {
            String date = txtReportDate.getText().trim();
            if (date.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a valid date.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String reportText = usageDAO.getDailyReportText(date);
            txtReportArea.setText(reportText);
        });

        return panel;
    }

    // ==========================================
    // CARD 5: SPEED METER PANEL
    // ==========================================
    private JPanel createSpeedMeterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("⚡ Real-Time Network Speed Meter");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        headerPanel.add(title, BorderLayout.NORTH);

        lblCurrentPing = new JLabel("Latency: Calculating...");
        lblCurrentPing.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblCurrentPing.setForeground(new Color(220, 220, 230));
        headerPanel.add(lblCurrentPing, BorderLayout.SOUTH);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Speed Display Grid
        JPanel speedCardsGrid = new JPanel(new GridLayout(1, 2, 20, 0));
        speedCardsGrid.setOpaque(false);

        JPanel dlCard = createStatCard("Download Speed", lblCurrentDownload = new JLabel("↓ 0.00 Mbps"), COLOR_ACCENT_GREEN);
        JPanel ulCard = createStatCard("Upload Speed", lblCurrentUpload = new JLabel("↑ 0.00 Mbps"), COLOR_ACCENT_BLUE);

        speedCardsGrid.add(dlCard);
        speedCardsGrid.add(ulCard);

        // Speed Graph container
        JPanel chartContainer = new JPanel(new BorderLayout(0, 10));
        chartContainer.setOpaque(false);
        chartContainer.setBorder(new EmptyBorder(15, 0, 0, 0));

        // Legend details
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        legendPanel.setOpaque(false);
        
        JLabel dlLegend = new JLabel("── Download (Mbps)");
        dlLegend.setForeground(COLOR_ACCENT_GREEN);
        dlLegend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        JLabel ulLegend = new JLabel("── Upload (Mbps)");
        ulLegend.setForeground(COLOR_ACCENT_BLUE);
        ulLegend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        legendPanel.add(dlLegend);
        legendPanel.add(ulLegend);
        chartContainer.add(legendPanel, BorderLayout.NORTH);

        // Live Custom Line Chart
        speedChart = new SpeedChartPanel();
        speedChart.setPreferredSize(new Dimension(0, 260));
        chartContainer.add(speedChart, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.add(speedCardsGrid, BorderLayout.NORTH);
        centerPanel.add(chartContainer, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    // ==========================================
    // WORKER: REAL-TIME BANDWIDTH TRACKER
    // ==========================================
    private void startSpeedMeter() {
        long[] initialBytes = getNetworkBytes();
        lastReceivedBytes = initialBytes[0];
        lastSentBytes = initialBytes[1];
        lastSpeedTimestamp = System.currentTimeMillis();

        Thread speedWorker = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }

                long[] currentBytes = getNetworkBytes();
                long currentTimestamp = System.currentTimeMillis();
                long timeDiffMs = currentTimestamp - lastSpeedTimestamp;
                if (timeDiffMs <= 0) timeDiffMs = 1000;

                long receivedDiff = currentBytes[0] - lastReceivedBytes;
                long sentDiff = currentBytes[1] - lastSentBytes;

                // Adjust for overflow or interface reset
                if (receivedDiff < 0) receivedDiff = 0;
                if (sentDiff < 0) sentDiff = 0;

                lastReceivedBytes = currentBytes[0];
                lastSentBytes = currentBytes[1];
                lastSpeedTimestamp = currentTimestamp;

                // Accumulate host usage data (in GB)
                double gbDiff = (receivedDiff + sentDiff) / (1024.0 * 1024.0 * 1024.0);
                accumulatedGb += gbDiff;
                loopCounter++;

                // Flush to database every 5 seconds asynchronously
                if (loopCounter % 5 == 0 && accumulatedGb > 0 && hostDeviceId != -1) {
                    final double toWrite = accumulatedGb;
                    accumulatedGb = 0.0;
                    new Thread(() -> {
                        usageDAO.incrementUsage(hostDeviceId, java.time.LocalDate.now().toString(), toWrite);
                        SwingUtilities.invokeLater(() -> refreshAllData());
                    }).start();
                }

                // Bytes per second
                double dlSpeedBps = (receivedDiff * 1000.0) / timeDiffMs;
                double ulSpeedBps = (sentDiff * 1000.0) / timeDiffMs;

                // Convert to Mbps (Megabits per second)
                double dlMbps = (dlSpeedBps * 8.0) / (1024.0 * 1024.0);
                double ulMbps = (ulSpeedBps * 8.0) / (1024.0 * 1024.0);

                // Convert to MB/s (Megabytes per second)
                double dlMBs = dlSpeedBps / (1024.0 * 1024.0);
                double ulMBs = ulSpeedBps / (1024.0 * 1024.0);

                // Measure ping
                int pingMs = measurePing("8.8.8.8");

                // Update Swing Components
                SwingUtilities.invokeLater(() -> {
                    if (lblCurrentDownload != null) {
                        lblCurrentDownload.setText(String.format("↓ %.2f Mbps (%.2f MB/s)", dlMbps, dlMBs));
                    }
                    if (lblCurrentUpload != null) {
                        lblCurrentUpload.setText(String.format("↑ %.2f Mbps (%.2f MB/s)", ulMbps, ulMBs));
                    }
                    if (lblCurrentPing != null) {
                        if (pingMs >= 0) {
                            lblCurrentPing.setText(String.format("Latency: %d ms", pingMs));
                        } else {
                            lblCurrentPing.setText("Latency: Offline");
                        }
                    }
                    if (speedChart != null) {
                        speedChart.addDataPoint(dlMbps, ulMbps);
                    }
                });
            }
        });
        speedWorker.setDaemon(true);
        speedWorker.start();
    }

    private long[] getNetworkBytes() {
        long[] bytes = new long[]{0, 0}; // {received, sent}
        try {
            Process process = Runtime.getRuntime().exec("netstat -e");
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Bytes")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            bytes[0] = Long.parseLong(parts[1]); // Received
                            bytes[1] = Long.parseLong(parts[2]); // Sent
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytes;
    }

    private int measurePing(String host) {
        try {
            long start = System.currentTimeMillis();
            Process p = Runtime.getRuntime().exec("ping -n 1 -w 1000 " + host);
            int exitVal = p.waitFor();
            if (exitVal == 0) {
                return (int) (System.currentTimeMillis() - start);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    // ==========================================
    // CUSTOM SPEED CHART COMPONENT
    // ==========================================
    static class SpeedChartPanel extends JPanel {
        private final java.util.List<Double> dlHistory = new java.util.ArrayList<>();
        private final java.util.List<Double> ulHistory = new java.util.ArrayList<>();
        private final int maxDataPoints = 30;

        public SpeedChartPanel() {
            setOpaque(false);
            // Pre-fill lists with 0.0 values
            for (int i = 0; i < maxDataPoints; i++) {
                dlHistory.add(0.0);
                ulHistory.add(0.0);
            }
        }

        public synchronized void addDataPoint(double dlMbps, double ulMbps) {
            dlHistory.remove(0);
            dlHistory.add(dlMbps);
            ulHistory.remove(0);
            ulHistory.add(ulMbps);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padding = 30;

            // Translucent frosted backing
            g2.setColor(new Color(255, 255, 255, 12));
            g2.fillRoundRect(0, 0, w, h, 15, 15);
            g2.setColor(new Color(255, 255, 255, 45));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 15, 15);

            // Draw horizontal grids
            g2.setColor(new Color(255, 255, 255, 20));
            int gridCount = 5;
            for (int i = 1; i < gridCount; i++) {
                int y = padding + i * (h - 2 * padding) / gridCount;
                g2.drawLine(padding, y, w - padding, y);
            }

            // Find max value in history to scale graph
            double maxSpeed = 5.0; // minimum chart limit
            synchronized (this) {
                for (double s : dlHistory) if (s > maxSpeed) maxSpeed = s;
                for (double s : ulHistory) if (s > maxSpeed) maxSpeed = s;
            }
            // Round scale to nearest 5 Mbps stop
            maxSpeed = Math.ceil(maxSpeed / 5.0) * 5.0;

            // Draw Y-axis scale markings
            g2.setColor(new Color(220, 220, 230));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.drawString(String.format("%.0f Mbps", maxSpeed), 6, padding + 4);
            g2.drawString(String.format("%.0f Mbps", maxSpeed / 2.0), 6, h / 2 + 4);
            g2.drawString("0 Mbps", 6, h - padding + 4);

            int chartWidth = w - 2 * padding;
            int chartHeight = h - 2 * padding;
            double stepX = (double) chartWidth / (maxDataPoints - 1);

            int[] xPoints = new int[maxDataPoints];
            int[] dlYPoints = new int[maxDataPoints];
            int[] ulYPoints = new int[maxDataPoints];

            synchronized (this) {
                for (int i = 0; i < maxDataPoints; i++) {
                    xPoints[i] = padding + (int) (i * stepX);
                    double dlVal = dlHistory.get(i);
                    double ulVal = ulHistory.get(i);
                    dlYPoints[i] = h - padding - (int) ((dlVal / maxSpeed) * chartHeight);
                    ulYPoints[i] = h - padding - (int) ((ulVal / maxSpeed) * chartHeight);
                }
            }

            // Draw Download Path (Neon Green)
            g2.setColor(new Color(48, 209, 88));
            g2.setStroke(new BasicStroke(2.5f));
            for (int i = 0; i < maxDataPoints - 1; i++) {
                g2.drawLine(xPoints[i], dlYPoints[i], xPoints[i + 1], dlYPoints[i + 1]);
            }

            // Draw Upload Path (Neon Blue)
            g2.setColor(new Color(10, 132, 255));
            g2.setStroke(new BasicStroke(2.0f));
            for (int i = 0; i < maxDataPoints - 1; i++) {
                g2.drawLine(xPoints[i], ulYPoints[i], xPoints[i + 1], ulYPoints[i + 1]);
            }

            g2.dispose();
        }
    }

    // ==========================================
    // GENERAL HELPERS & UI STYLING
    // ==========================================
    private JTextField createGlassFormFields(JPanel parent, String labelText) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(new Color(200, 200, 210));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createRigidArea(new Dimension(0, 6)));

        JTextField txt = new LoginSignupUI.GlassTextField();
        txt.setMaximumSize(new Dimension(260, 38));
        txt.setPreferredSize(new Dimension(260, 38));
        txt.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(txt);
        parent.add(Box.createRigidArea(new Dimension(0, 15)));

        return txt;
    }

    private void styleGlassTable(JTable table) {
        table.setOpaque(false);
        table.setBackground(new Color(0, 0, 0, 0));
        table.setForeground(Color.WHITE);
        table.setGridColor(new Color(255, 255, 255, 45));
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setSelectionBackground(new Color(255, 255, 255, 55));
        table.setSelectionForeground(Color.WHITE);

        // Header Styling
        JTableHeader header = table.getTableHeader();
        header.setOpaque(false);
        header.setBackground(new Color(0, 0, 0, 50));
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 60)));
        header.setPreferredSize(new Dimension(header.getWidth(), 36));

        // Glass row rendering
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, isSelected, hasFocus, row, col);
                c.setForeground(Color.WHITE);
                
                // Frosted translucent row background
                if (isSelected) {
                    c.setBackground(new Color(255, 255, 255, 60)); // Active highlight
                } else {
                    c.setBackground(new Color(255, 255, 255, 15)); // standard glass row
                }
                
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    label.setOpaque(true);
                    label.setBorder(new EmptyBorder(0, 10, 0, 10));
                    if (col == 0 || col == 1 || col == 4) {
                        label.setHorizontalAlignment(SwingConstants.LEFT);
                    } else {
                        label.setHorizontalAlignment(SwingConstants.CENTER);
                    }
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, cellRenderer);
    }

    private void refreshAllData() {
        // Fetch devices from Database
        List<Device> devices = deviceDAO.getAllDevices();

        // 1. Update Devices Table
        modelDevices.setRowCount(0);
        cmbDeviceSelect.removeAllItems();

        if (devices.isEmpty()) {
            cmbDeviceSelect.addItem("No devices registered");
        }

        for (Device d : devices) {
            modelDevices.addRow(new Object[]{
                    d.getDeviceId(),
                    d.getDeviceName(),
                    d.getOwnerName(),
                    d.getDeviceType(),
                    d.getDataLimit()
            });
            cmbDeviceSelect.addItem(String.format("ID: %d | %s (%s)", d.getDeviceId(), d.getDeviceName(), d.getOwnerName()));
        }

        // 2. Update Usage Log Table
        List<UsageRecord> usageRecords = usageDAO.getAllUsageRecords();
        modelUsage.setRowCount(0);

        for (UsageRecord r : usageRecords) {
            String devInfo = "Unknown Device";
            for (Device d : devices) {
                if (d.getDeviceId() == r.getDeviceId()) {
                    devInfo = String.format("%s (%s)", d.getDeviceName(), d.getOwnerName());
                    break;
                }
            }

            modelUsage.addRow(new Object[]{
                    r.getUsageId(),
                    r.getDeviceId(),
                    devInfo,
                    r.getUsageDate(),
                    r.getDataUsed()
            });
        }

        // 3. Update Home Tab Cards
        lblTotalDevicesVal.setText(String.valueOf(devices.size()));

        double totalUsage = reportGenerator.getTotalConsumption();
        lblTotalUsageVal.setText(String.format("%.2f GB", totalUsage));

        List<String> alerts = reportGenerator.getLimitAlerts();
        lblActiveAlertsVal.setText(String.valueOf(alerts.size()));

        // Display Alerts in Home Tab list
        StringBuilder alertsBuilder = new StringBuilder();
        if (alerts.isEmpty()) {
            alertsBuilder.append("🟢 All devices are operating within their data limits. No alerts.");
        } else {
            for (String alert : alerts) {
                alertsBuilder.append(alert).append("\n");
            }
        }
        txtAlertList.setText(alertsBuilder.toString());

        // Update Reports Text Area if date exists
        if (txtReportDate != null && !txtReportDate.getText().isEmpty()) {
            txtReportArea.setText(usageDAO.getDailyReportText(txtReportDate.getText()));
        }
    }

    // ==========================================
    // AUTO-DETECTION SCANNER LOGIC
    // ==========================================
    private void showAutoDetectDialog(JTextField txtDevName, JComboBox<String> cmbType) {
        JDialog dialog = new JDialog(this, "Auto-Discover Connected Devices", true);
        dialog.setSize(600, 420);
        dialog.setLocationRelativeTo(this);

        // Gradient Background Panel
        JPanel mainPanel = new LoginSignupUI.GradientBackgroundPanel();
        mainPanel.setLayout(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("⚡ Auto-Discover Local Devices");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        mainPanel.add(title, BorderLayout.NORTH);

        // Status description
        JLabel statusLabel = new JLabel("🔍 Discovering active network devices...");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        statusLabel.setForeground(new Color(220, 220, 230));

        // Glass Table of discovered devices
        String[] cols = {"IP Address", "MAC Address", "Hostname", "Guessed Type"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable table = new JTable(model);
        styleGlassTable(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 45)));

        // Action Controls
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setOpaque(false);

        JButton btnImport = new LoginSignupUI.GlassButton("Import Selected", COLOR_ACCENT_BLUE, 40);
        btnImport.setPreferredSize(new Dimension(145, 36));
        btnImport.setEnabled(false);

        JButton btnCancel = new LoginSignupUI.GlassButton("Cancel", new Color(255, 255, 255), 25);
        btnCancel.setPreferredSize(new Dimension(90, 36));

        buttonPanel.add(btnImport);
        buttonPanel.add(btnCancel);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        dialog.add(mainPanel);

        // Selection Listener
        table.getSelectionModel().addListSelectionListener(event -> {
            btnImport.setEnabled(table.getSelectedRow() != -1);
        });

        // Import Listener
        btnImport.addActionListener(evt -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                String ip = (String) model.getValueAt(row, 0);
                String mac = (String) model.getValueAt(row, 1);
                String hostname = (String) model.getValueAt(row, 2);
                String guessedType = (String) model.getValueAt(row, 3);

                if ("Resolving...".equals(guessedType) || "Unknown".equals(hostname)) {
                    guessedType = "Other";
                }

                String cleanedName = cleanDeviceName(hostname, ip);
                txtDevName.setText(cleanedName);
                cmbType.setSelectedItem(guessedType);
                dialog.dispose();
            }
        });

        btnCancel.addActionListener(evt -> dialog.dispose());

        // Perform parallel async subnet sweep followed by device host resolution
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText("🔍 Sweeping network to detect mobile/wireless devices..."));
            
            // Perform Active Subnet Ping Sweep
            List<String> subnets = getLocalSubnets();
            performSubnetPingSweep(subnets);
            
            SwingUtilities.invokeLater(() -> statusLabel.setText("🔍 Querying discovered devices..."));
            
            List<DiscoveredDevice> discovered = discoverLocalDevices();
            SwingUtilities.invokeLater(() -> {
                if (discovered.isEmpty()) {
                    statusLabel.setText("No active dynamic devices found in ARP table.");
                } else {
                    statusLabel.setText(String.format("Found %d devices. Resolving names...", discovered.size()));
                }
                for (DiscoveredDevice dev : discovered) {
                    model.addRow(new Object[]{dev.ip, dev.mac, "Resolving...", "Resolving..."});
                }
            });

            // Parallel lookup threads
            for (int i = 0; i < discovered.size(); i++) {
                final int index = i;
                final DiscoveredDevice dev = discovered.get(i);
                new Thread(() -> {
                    try {
                        java.net.InetAddress addr = java.net.InetAddress.getByName(dev.ip);
                        String hostname = addr.getHostName();
                        String guessedType = guessDeviceType(hostname, dev.mac);

                        SwingUtilities.invokeLater(() -> {
                            model.setValueAt(hostname, index, 2);
                            model.setValueAt(guessedType, index, 3);
                            // Highlight update status when final item resolves
                            if (index == discovered.size() - 1) {
                                statusLabel.setText("Device details resolution completed.");
                            }
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            model.setValueAt("Unknown", index, 2);
                            model.setValueAt("Other", index, 3);
                        });
                    }
                }).start();
            }
        }).start();

        dialog.setVisible(true);
    }

    private List<DiscoveredDevice> discoverLocalDevices() {
        List<DiscoveredDevice> list = new java.util.ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec("arp -a");
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("Interface:") || line.startsWith("Internet Address")) {
                        continue;
                    }
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        String ip = parts[0];
                        String mac = parts[1];
                        String type = parts[2];

                        // Skip static multicast/broadcast
                        if (!"dynamic".equalsIgnoreCase(type)) {
                            continue;
                        }

                        list.add(new DiscoveredDevice(ip, mac, "Resolving...", "Other"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private String guessDeviceType(String hostname, String mac) {
        String name = hostname.toLowerCase();
        if (name.contains("phone") || name.contains("android") || name.contains("iphone") || name.contains("ipad") || name.contains("galaxy") || name.contains("pixel") || name.contains("mobile") || name.contains("huawei") || name.contains("xiaomi") || name.contains("oneplus")) {
            return "Mobile";
        }
        if (name.contains("tv") || name.contains("chromecast") || name.contains("roku") || name.contains("fire") || name.contains("appletv") || name.contains("bravia") || name.contains("smart") || name.contains("shield")) {
            return "Smart TV";
        }
        if (name.contains("laptop") || name.contains("desktop") || name.contains("pc") || name.contains("macbook") || name.contains("computer") || name.contains("workstation")) {
            return "Laptop";
        }
        return "Other";
    }

    private String cleanDeviceName(String hostname, String ip) {
        if (hostname == null || hostname.isEmpty() || hostname.equalsIgnoreCase(ip)) {
            return "Unknown Device (" + ip + ")";
        }
        String clean = hostname;
        if (clean.endsWith(".local")) {
            clean = clean.substring(0, clean.length() - 6);
        }
        if (clean.endsWith(".home")) {
            clean = clean.substring(0, clean.length() - 5);
        }
        if (clean.endsWith(".lan")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        clean = clean.replace("-", " ").replace("_", " ");
        // Capitalize words
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void initHostDevice() {
        try {
            String rawHost = java.net.InetAddress.getLocalHost().getHostName();
            String hostName = cleanDeviceName(rawHost, "127.0.0.1");

            // Query all devices to see if one matches hostName
            List<Device> devices = deviceDAO.getAllDevices();
            for (Device d : devices) {
                if (d.getDeviceName().equalsIgnoreCase(hostName)) {
                    hostDeviceId = d.getDeviceId();
                    return;
                }
            }

            // If not found, register it automatically!
            Device autoDev = new Device(0, hostName, "Current User", "Laptop", 50.0);
            deviceDAO.addDevice(autoDev);

            // Re-query to get the ID
            devices = deviceDAO.getAllDevices();
            for (Device d : devices) {
                if (d.getDeviceName().equalsIgnoreCase(hostName)) {
                    hostDeviceId = d.getDeviceId();
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("Error initializing host device: " + e.getMessage());
        }
    }

    private List<String> getLocalSubnets() {
        List<String> subnets = new java.util.ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                
                for (java.net.InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                    java.net.InetAddress addr = ifaceAddr.getAddress();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("127.") || ip.startsWith("169.254")) continue;
                        
                        short prefixLen = ifaceAddr.getNetworkPrefixLength();
                        if (prefixLen >= 24) { // /24 subnet, e.g. 192.168.1.x
                            int lastDot = ip.lastIndexOf('.');
                            if (lastDot != -1) {
                                subnets.add(ip.substring(0, lastDot + 1));
                            }
                        } else if (prefixLen >= 16) { // /16 subnet, e.g. 10.1.x.x
                            int firstDot = ip.indexOf('.');
                            int secondDot = ip.indexOf('.', firstDot + 1);
                            if (firstDot != -1 && secondDot != -1) {
                                String classB = ip.substring(0, secondDot + 1); // e.g. "10.1."
                                int thirdDot = ip.indexOf('.', secondDot + 1);
                                if (thirdDot != -1) {
                                    int thirdOctet = Integer.parseInt(ip.substring(secondDot + 1, thirdDot));
                                    for (int oct = Math.max(0, thirdOctet - 1); oct <= Math.min(255, thirdOctet + 1); oct++) {
                                        subnets.add(classB + oct + ".");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        
        // Fallback standard subnet if empty
        if (subnets.isEmpty()) {
            subnets.add("192.168.1.");
        }
        return subnets;
    }

    private void performSubnetPingSweep(List<String> subnets) {
        if (subnets == null || subnets.isEmpty()) return;
        
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(64);
        for (String prefix : subnets) {
            for (int i = 1; i <= 254; i++) {
                final String targetIp = prefix + i;
                executor.submit(() -> {
                    try {
                        java.net.InetAddress address = java.net.InetAddress.getByName(targetIp);
                        address.isReachable(400); // 400ms timeout
                    } catch (Exception ignored) {}
                });
            }
        }
        executor.shutdown();
        try {
            // Wait up to 1.5 seconds for pings to complete
            executor.awaitTermination(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
    }

    // Discovered Device Model Struct
    static class DiscoveredDevice {
        public String ip;
        public String mac;
        public String hostname;
        public String guessedType;

        public DiscoveredDevice(String ip, String mac, String hostname, String guessedType) {
            this.ip = ip;
            this.mac = mac;
            this.hostname = hostname;
            this.guessedType = guessedType;
        }
    }
}
