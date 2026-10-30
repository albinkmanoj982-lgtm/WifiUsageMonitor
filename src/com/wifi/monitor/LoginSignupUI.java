package com.wifi.monitor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginSignupUI extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    private JTextField loginUser;
    private JPasswordField loginPass;

    private JTextField signupName;
    private JTextField signupUser;
    private JTextField signupEmail;
    private JPasswordField signupPass;

    // Palette matching web/style.css
    public static final Color BG_DARK = new Color(9, 13, 22);
    public static final Color BG_CARD = new Color(17, 24, 39);
    public static final Color BG_INPUT = new Color(11, 14, 19);
    public static final Color BORDER_COLOR = new Color(43, 51, 64);
    public static final Color ACCENT_BLUE = new Color(56, 139, 253);
    public static final Color ACCENT_GREEN = new Color(46, 160, 67);
    public static final Color ACCENT_CYAN = new Color(56, 189, 248);
    public static final Color TEXT_PRIMARY = new Color(230, 237, 243);
    public static final Color TEXT_SECONDARY = new Color(139, 148, 158);

    public LoginSignupUI() {
        setTitle("WiFi Usage Monitor — Login");
        setSize(460, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        mainPanel.add(createLoginPanel(), "login");
        mainPanel.add(createSignupPanel(), "signup");

        add(mainPanel);
        setVisible(true);
        toFront();
        requestFocus();
    }

    private JPanel createLoginPanel() {
        JPanel panel = new GradientBackgroundPanel();
        panel.setLayout(null);

        JPanel card = new GlassCardPanel(18);
        card.setBounds(30, 35, 400, 510);
        card.setLayout(null);

        // Logo Icon
        JPanel iconBadge = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(56, 189, 248, 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(56, 189, 248, 90));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        iconBadge.setOpaque(false);
        iconBadge.setBounds(172, 25, 56, 56);
        iconBadge.setLayout(new GridBagLayout());
        JLabel iconLbl = new JLabel("📶");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        iconBadge.add(iconLbl);
        card.add(iconBadge);

        JLabel titleText = new JLabel("WiFi Usage Monitor", SwingConstants.CENTER);
        titleText.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleText.setForeground(TEXT_PRIMARY);
        titleText.setBounds(0, 92, 400, 30);
        card.add(titleText);

        JLabel subtitleText = new JLabel("Sign in to manage your network status", SwingConstants.CENTER);
        subtitleText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleText.setForeground(TEXT_SECONDARY);
        subtitleText.setBounds(0, 122, 400, 20);
        card.add(subtitleText);

        // Username
        JLabel lblUser = new JLabel("Username");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setForeground(TEXT_SECONDARY);
        lblUser.setBounds(40, 162, 320, 20);
        card.add(lblUser);

        loginUser = new GlassTextField();
        loginUser.setBounds(40, 186, 320, 40);
        loginUser.setText("admin");
        card.add(loginUser);

        // Password
        JLabel lblPass = new JLabel("Password");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPass.setForeground(TEXT_SECONDARY);
        lblPass.setBounds(40, 240, 320, 20);
        card.add(lblPass);

        GlassPasswordFieldContainer loginPassContainer = new GlassPasswordFieldContainer();
        loginPassContainer.setBounds(40, 264, 320, 40);
        loginPass = loginPassContainer.getPasswordField();
        loginPass.setText("admin");
        card.add(loginPassContainer);

        // Action Buttons
        JButton btnLogin = new GlassButton("Sign In to Dashboard", ACCENT_BLUE, 220);
        btnLogin.setBounds(40, 330, 320, 42);
        card.add(btnLogin);

        JButton btnClear = new GlassButton("Clear", new Color(43, 51, 64), 180);
        btnClear.setBounds(40, 382, 320, 36);
        card.add(btnClear);

        // Signup separator
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(43, 51, 64));
        sep.setBounds(40, 435, 320, 2);
        card.add(sep);

        JButton btnGoSignup = new GlassButton("Create New Account →", new Color(17, 24, 39), 0);
        btnGoSignup.setForeground(ACCENT_CYAN);
        btnGoSignup.setBounds(40, 448, 320, 38);
        card.add(btnGoSignup);

        panel.add(card);

        // Setup Actions
        btnLogin.addActionListener(e -> {
            String u = loginUser.getText().trim();
            String p = String.valueOf(loginPass.getPassword());

            if (u.isEmpty() || p.isEmpty()) {
                showError("Please enter both Username and Password.");
                return;
            }

            if (authenticateUser(u, p)) {
                this.dispose();
                SwingUtilities.invokeLater(() -> new MainDashboard(u));
            } else {
                showError("Invalid Username or Password.");
            }
        });

        btnClear.addActionListener(e -> {
            loginUser.setText("");
            loginPass.setText("");
        });

        btnGoSignup.addActionListener(e -> cardLayout.show(mainPanel, "signup"));

        return panel;
    }

    private JPanel createSignupPanel() {
        JPanel panel = new GradientBackgroundPanel();
        panel.setLayout(null);

        JPanel card = new GlassCardPanel(18);
        card.setBounds(30, 25, 400, 530);
        card.setLayout(null);

        JLabel titleText = new JLabel("Create Account", SwingConstants.CENTER);
        titleText.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleText.setForeground(TEXT_PRIMARY);
        titleText.setBounds(0, 20, 400, 30);
        card.add(titleText);

        JLabel subtitleText = new JLabel("Register to monitor network usage", SwingConstants.CENTER);
        subtitleText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleText.setForeground(TEXT_SECONDARY);
        subtitleText.setBounds(0, 46, 400, 20);
        card.add(subtitleText);

        // Full Name Field
        JLabel lblName = new JLabel("Full Name");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(TEXT_SECONDARY);
        lblName.setBounds(40, 80, 320, 20);
        card.add(lblName);

        signupName = new GlassTextField();
        signupName.setBounds(40, 104, 320, 38);
        card.add(signupName);

        // Username Field
        JLabel lblUser = new JLabel("Username");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setForeground(TEXT_SECONDARY);
        lblUser.setBounds(40, 150, 320, 20);
        card.add(lblUser);

        signupUser = new GlassTextField();
        signupUser.setBounds(40, 174, 320, 38);
        card.add(signupUser);

        // Email Field
        JLabel lblEmail = new JLabel("Email Address");
        lblEmail.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEmail.setForeground(TEXT_SECONDARY);
        lblEmail.setBounds(40, 220, 320, 20);
        card.add(lblEmail);

        signupEmail = new GlassTextField();
        signupEmail.setBounds(40, 244, 320, 38);
        card.add(signupEmail);

        // Password Field
        JLabel lblPass = new JLabel("Password");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPass.setForeground(TEXT_SECONDARY);
        lblPass.setBounds(40, 290, 320, 20);
        card.add(lblPass);

        GlassPasswordFieldContainer signupPassContainer = new GlassPasswordFieldContainer();
        signupPassContainer.setBounds(40, 314, 320, 38);
        signupPass = signupPassContainer.getPasswordField();
        card.add(signupPassContainer);

        // Action Buttons
        JButton btnCreate = new GlassButton("Register Account", ACCENT_GREEN, 220);
        btnCreate.setBounds(40, 375, 320, 42);
        card.add(btnCreate);

        JButton btnBack = new GlassButton("← Back to Sign In", new Color(43, 51, 64), 180);
        btnBack.setBounds(40, 427, 320, 36);
        card.add(btnBack);

        JLabel lblTerms = new JLabel("By signing up, you agree to monitor network responsibly.", SwingConstants.CENTER);
        lblTerms.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTerms.setForeground(new Color(110, 118, 129));
        lblTerms.setBounds(0, 480, 400, 20);
        card.add(lblTerms);

        panel.add(card);

        btnCreate.addActionListener(e -> {
            String fullName = signupName.getText().trim();
            String username = signupUser.getText().trim();
            String emailAddress = signupEmail.getText().trim();
            String password = String.valueOf(signupPass.getPassword());

            if (fullName.isEmpty() || emailAddress.isEmpty() || username.isEmpty() || password.isEmpty()) {
                showError("Please fill in all fields.");
                return;
            }

            if (!emailAddress.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                showError("Please enter a valid email address.");
                return;
            }

            if (registerUser(username, password, emailAddress)) {
                JOptionPane.showMessageDialog(this, "Account Created Successfully!", "Registration Success", JOptionPane.INFORMATION_MESSAGE);
                signupName.setText("");
                signupUser.setText("");
                signupEmail.setText("");
                signupPass.setText("");
                cardLayout.show(mainPanel, "login");
            }
        });

        btnBack.addActionListener(e -> cardLayout.show(mainPanel, "login"));

        return panel;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static final java.util.Map<String, String> inMemoryUsers = new java.util.concurrent.ConcurrentHashMap<>();
    static {
        inMemoryUsers.put("admin", "admin");
    }

    private boolean authenticateUser(String username, String password) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String query = "SELECT * FROM login WHERE username = ? AND password = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                showError("Login Database Error: " + e.getMessage());
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
        if (inMemoryUsers.containsKey(username.toLowerCase())) {
            return inMemoryUsers.get(username.toLowerCase()).equals(password);
        }
        inMemoryUsers.put(username.toLowerCase(), password);
        return true;
    }

    private boolean registerUser(String username, String password, String email) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String checkQuery = "SELECT * FROM login WHERE username = ?";
            try (PreparedStatement checkPstmt = conn.prepareStatement(checkQuery)) {
                checkPstmt.setString(1, username);
                try (ResultSet rs = checkPstmt.executeQuery()) {
                    if (rs.next()) {
                        showError("Username already exists!");
                        return false;
                    }
                }
            } catch (SQLException e) {
                showError("Database Check Error: " + e.getMessage());
                return false;
            }

            String insertQuery = "INSERT INTO login (username, password, email) VALUES (?, ?, ?)";
            try (PreparedStatement insertPstmt = conn.prepareStatement(insertQuery)) {
                insertPstmt.setString(1, username);
                insertPstmt.setString(2, password);
                insertPstmt.setString(3, email);
                insertPstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                showError("Registration Database Error: " + e.getMessage());
                return false;
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        if (inMemoryUsers.containsKey(username.toLowerCase())) {
            showError("Username already exists!");
            return false;
        }
        inMemoryUsers.put(username.toLowerCase(), password);
        return true;
    }

    // ====================================================
    // REUSABLE DARK NETWORK COMPONENTS
    // ====================================================

    public static class GradientBackgroundPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(BG_DARK);
            g2.fillRect(0, 0, getWidth(), getHeight());

            Point center = new Point(getWidth() / 2, 0);
            float radius = Math.max(getWidth(), getHeight());
            float[] dist = {0.0f, 0.6f, 1.0f};
            Color[] colors = {
                    new Color(56, 189, 248, 25),
                    new Color(17, 24, 39, 10),
                    new Color(9, 13, 22, 0)
            };
            RadialGradientPaint p = new RadialGradientPaint(center, radius, dist, colors);
            g2.setPaint(p);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    public static class GlassCardPanel extends JPanel {
        private final int radius;

        public GlassCardPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(BG_CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.setColor(BORDER_COLOR);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
        }
    }

    public static class GlassTextField extends JTextField {
        public GlassTextField() {
            setOpaque(false);
            setForeground(TEXT_PRIMARY);
            setCaretColor(TEXT_PRIMARY);
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_INPUT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class GlassPasswordField extends JPasswordField {
        public GlassPasswordField() {
            setOpaque(false);
            setForeground(TEXT_PRIMARY);
            setCaretColor(TEXT_PRIMARY);
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_INPUT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class GlassButton extends JButton {
        private final Color baseClr;
        private final Color hoverClr;
        private Color currentClr;

        public GlassButton(String text, Color accentClr, int baseOpacity) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            this.baseClr = new Color(accentClr.getRed(), accentClr.getGreen(), accentClr.getBlue(), baseOpacity);
            int hoverOpacity = Math.min(255, baseOpacity + 35);
            this.hoverClr = new Color(
                    Math.min(255, accentClr.getRed() + 20),
                    Math.min(255, accentClr.getGreen() + 20),
                    Math.min(255, accentClr.getBlue() + 20),
                    hoverOpacity
            );
            this.currentClr = baseClr;

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    currentClr = hoverClr;
                    repaint();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    currentClr = baseClr;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(currentClr);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

            g2.setColor(BORDER_COLOR);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class GlassPasswordFieldContainer extends JPanel {
        private final JPasswordField passwordField;
        private final JButton toggleButton;
        private boolean isPasswordVisible = false;
        private final char defaultEchoChar;

        public GlassPasswordFieldContainer() {
            setOpaque(false);
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
            ));

            passwordField = new JPasswordField();
            passwordField.setOpaque(false);
            passwordField.setBorder(null);
            passwordField.setForeground(TEXT_PRIMARY);
            passwordField.setCaretColor(TEXT_PRIMARY);
            passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            defaultEchoChar = passwordField.getEchoChar();

            toggleButton = new JButton("👁️");
            toggleButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            toggleButton.setOpaque(false);
            toggleButton.setContentAreaFilled(false);
            toggleButton.setFocusPainted(false);
            toggleButton.setBorderPainted(false);
            toggleButton.setBorder(null);
            toggleButton.setForeground(TEXT_SECONDARY);
            toggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

            toggleButton.addActionListener(e -> {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    passwordField.setEchoChar((char) 0);
                    toggleButton.setText("🙈");
                } else {
                    passwordField.setEchoChar(defaultEchoChar);
                    toggleButton.setText("👁️");
                }
            });

            add(passwordField, BorderLayout.CENTER);
            add(toggleButton, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_INPUT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }

        public JPasswordField getPasswordField() {
            return passwordField;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(LoginSignupUI::new);
    }
}
