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

    public LoginSignupUI() {
        setTitle("WiFi Usage Monitor");
        setSize(460, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        mainPanel.add(createLoginPanel(), "login");
        mainPanel.add(createSignupPanel(), "signup");

        add(mainPanel);
        setVisible(true);
    }

    private JPanel createLoginPanel() {
        // Multi-color Gradient Canvas
        JPanel panel = new GradientBackgroundPanel();
        panel.setLayout(null);

        // Glass Card Container
        JPanel card = new GlassCardPanel(20);
        card.setBounds(30, 40, 400, 490);
        card.setLayout(null);

        // Header Title
        JLabel titleIcon = new JLabel("📶", SwingConstants.CENTER);
        titleIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 46));
        titleIcon.setBounds(0, 30, 400, 50);
        card.add(titleIcon);

        JLabel titleText = new JLabel("WiFi Usage Monitor", SwingConstants.CENTER);
        titleText.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleText.setForeground(Color.WHITE);
        titleText.setBounds(0, 85, 400, 30);
        card.add(titleText);

        JLabel subtitleText = new JLabel("Sign in to manage your network status", SwingConstants.CENTER);
        subtitleText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleText.setForeground(new Color(220, 220, 230));
        subtitleText.setBounds(0, 115, 400, 20);
        card.add(subtitleText);

        // Username
        JLabel lblUser = new JLabel("Username");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setForeground(new Color(200, 200, 210));
        lblUser.setBounds(40, 160, 320, 20);
        card.add(lblUser);

        loginUser = new GlassTextField();
        loginUser.setBounds(40, 185, 320, 40);
        card.add(loginUser);

        // Password
        JLabel lblPass = new JLabel("Password");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPass.setForeground(new Color(200, 200, 210));
        lblPass.setBounds(40, 240, 320, 20);
        card.add(lblPass);

        GlassPasswordFieldContainer loginPassContainer = new GlassPasswordFieldContainer();
        loginPassContainer.setBounds(40, 265, 320, 40);
        loginPass = loginPassContainer.getPasswordField();
        card.add(loginPassContainer);

        // Action Buttons
        JButton btnLogin = new GlassButton("Login", new Color(10, 132, 255), 45); // Glass Blue accent
        btnLogin.setBounds(40, 335, 150, 42);
        card.add(btnLogin);

        JButton btnClear = new GlassButton("Clear", new Color(255, 255, 255), 25); // Muted Glass white
        btnClear.setBounds(210, 335, 150, 42);
        card.add(btnClear);

        // Signup separator and link
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 255, 255, 50));
        sep.setBounds(40, 405, 320, 2);
        card.add(sep);

        JButton btnGoSignup = new GlassButton("Create New Account", new Color(255, 255, 255), 20);
        btnGoSignup.setBounds(40, 425, 320, 40);
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
                JOptionPane.showMessageDialog(this, "Welcome " + u + "!", "Login Successful", JOptionPane.INFORMATION_MESSAGE);
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
        // Multi-color Gradient Canvas
        JPanel panel = new GradientBackgroundPanel();
        panel.setLayout(null);

        // Glass Card Container
        JPanel card = new GlassCardPanel(20);
        card.setBounds(30, 25, 400, 515);
        card.setLayout(null);

        // Header Title
        JLabel titleText = new JLabel("Create Account", SwingConstants.CENTER);
        titleText.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleText.setForeground(Color.WHITE);
        titleText.setBounds(0, 20, 400, 30);
        card.add(titleText);

        JLabel subtitleText = new JLabel("Register to monitor network usage", SwingConstants.CENTER);
        subtitleText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleText.setForeground(new Color(220, 220, 230));
        subtitleText.setBounds(0, 45, 400, 20);
        card.add(subtitleText);

        // Full Name Field
        JLabel lblName = new JLabel("Full Name");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(new Color(200, 200, 210));
        lblName.setBounds(40, 80, 320, 20);
        card.add(lblName);

        signupName = new GlassTextField();
        signupName.setBounds(40, 105, 320, 38);
        card.add(signupName);

        // Username Field
        JLabel lblUser = new JLabel("Username");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setForeground(new Color(200, 200, 210));
        lblUser.setBounds(40, 155, 320, 20);
        card.add(lblUser);

        signupUser = new GlassTextField();
        signupUser.setBounds(40, 180, 320, 38);
        card.add(signupUser);

        // Email Field
        JLabel lblEmail = new JLabel("Email Address");
        lblEmail.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEmail.setForeground(new Color(200, 200, 210));
        lblEmail.setBounds(40, 230, 320, 20);
        card.add(lblEmail);

        signupEmail = new GlassTextField();
        signupEmail.setBounds(40, 255, 320, 38);
        card.add(signupEmail);

        // Password Field
        JLabel lblPass = new JLabel("Password");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPass.setForeground(new Color(200, 200, 210));
        lblPass.setBounds(40, 305, 320, 20);
        card.add(lblPass);

        GlassPasswordFieldContainer signupPassContainer = new GlassPasswordFieldContainer();
        signupPassContainer.setBounds(40, 330, 320, 38);
        signupPass = signupPassContainer.getPasswordField();
        card.add(signupPassContainer);

        // Action Buttons
        JButton btnCreate = new GlassButton("Sign Up", new Color(48, 209, 88), 45); // Glass Green accent
        btnCreate.setBounds(40, 395, 150, 42);
        card.add(btnCreate);

        JButton btnBack = new GlassButton("Back", new Color(255, 255, 255), 25);
        btnBack.setBounds(210, 395, 150, 42);
        card.add(btnBack);

        // Terms info
        JLabel lblTerms = new JLabel("By signing up, you agree to monitor network responsibly.", SwingConstants.CENTER);
        lblTerms.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTerms.setForeground(new Color(200, 200, 210));
        lblTerms.setBounds(0, 465, 400, 20);
        card.add(lblTerms);

        panel.add(card);

        // Setup Actions
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

    private boolean authenticateUser(String username, String password) {
        String query = "SELECT * FROM login WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            if (conn != null) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            showError("Login Database Error: " + e.getMessage());
        }
        return false;
    }

    private boolean registerUser(String username, String password, String email) {
        // First check if username exists
        String checkQuery = "SELECT * FROM login WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkPstmt = conn.prepareStatement(checkQuery)) {
            if (conn != null) {
                checkPstmt.setString(1, username);
                try (ResultSet rs = checkPstmt.executeQuery()) {
                    if (rs.next()) {
                        showError("Username already exists!");
                        return false;
                    }
                }
            }
        } catch (SQLException e) {
            showError("Database Check Error: " + e.getMessage());
            return false;
        }

        // Insert new user
        String insertQuery = "INSERT INTO login (username, password, email) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement insertPstmt = conn.prepareStatement(insertQuery)) {
            if (conn != null) {
                insertPstmt.setString(1, username);
                insertPstmt.setString(2, password);
                insertPstmt.setString(3, email);
                insertPstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            showError("Registration Database Error: " + e.getMessage());
        }
        return false;
    }

    // ====================================================
    // CUSTOM GLASSMORPHIC SWING COMPONENTS
    // ====================================================

    // 1. Colorful Multi-stop Gradient Panel
    static class GradientBackgroundPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Three-color linear gradient stops for a vibrant green aesthetic
            float[] fractions = {0.0f, 0.5f, 1.0f};
            Color[] colors = {
                    new Color(4, 38, 19),    // Dark forest green
                    new Color(11, 76, 44),   // Emerald green
                    new Color(25, 135, 84)   // Mint green
            };

            LinearGradientPaint lgp = new LinearGradientPaint(
                    0, 0, getWidth(), getHeight(), fractions, colors
            );
            g2d.setPaint(lgp);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.dispose();
        }
    }

    // 2. Frosted Glass Panel Container
    static class GlassCardPanel extends JPanel {
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

            // Translucent frosted glass effect
            g2.setColor(new Color(255, 255, 255, 24)); // White at 9% opacity
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            // Subtle inner glow border
            g2.setColor(new Color(255, 255, 255, 60)); // White at 23% opacity
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
        }
    }

    // 3. Glassmorphic Text Field
    static class GlassTextField extends JTextField {
        public GlassTextField() {
            setOpaque(false);
            setForeground(Color.WHITE);
            setCaretColor(Color.WHITE);
            setFont(new Font("Segoe UI", Font.PLAIN, 14));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 255, 255, 45), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Frosted background
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // 4. Glassmorphic Password Field
    static class GlassPasswordField extends JPasswordField {
        public GlassPasswordField() {
            setOpaque(false);
            setForeground(Color.WHITE);
            setCaretColor(Color.WHITE);
            setFont(new Font("Segoe UI", Font.PLAIN, 14));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 255, 255, 45), 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Frosted background
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // 5. Glassmorphic Interactive Button
    static class GlassButton extends JButton {
        private final Color baseClr;
        private final Color hoverClr;
        private final Color borderClr;
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
            this.hoverClr = new Color(accentClr.getRed(), accentClr.getGreen(), accentClr.getBlue(), baseOpacity + 35);
            this.borderClr = new Color(accentClr.getRed(), accentClr.getGreen(), accentClr.getBlue(), 125);
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
            // Background fill
            g2.setColor(currentClr);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            // Bright border highlight
            g2.setColor(borderClr);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // 6. Glassmorphic Password Field Container with Show/Hide Toggle Button
    static class GlassPasswordFieldContainer extends JPanel {
        private final JPasswordField passwordField;
        private final JButton toggleButton;
        private boolean isPasswordVisible = false;
        private final char defaultEchoChar;

        public GlassPasswordFieldContainer() {
            setOpaque(false);
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 255, 255, 45), 1),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)
            ));

            passwordField = new JPasswordField();
            passwordField.setOpaque(false);
            passwordField.setBorder(null);
            passwordField.setForeground(Color.WHITE);
            passwordField.setCaretColor(Color.WHITE);
            passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            defaultEchoChar = passwordField.getEchoChar();

            toggleButton = new JButton("👁️");
            toggleButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            toggleButton.setOpaque(false);
            toggleButton.setContentAreaFilled(false);
            toggleButton.setFocusPainted(false);
            toggleButton.setBorderPainted(false);
            toggleButton.setBorder(null);
            toggleButton.setForeground(new Color(220, 220, 230));
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
            // Frosted background
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
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
