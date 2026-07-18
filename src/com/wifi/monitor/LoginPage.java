package com.wifi.monitor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginPage extends JFrame implements ActionListener {

    JTextField txtUser;
    JPasswordField txtPass;
    JButton btnLogin, btnClear;

    public LoginPage() {
        setTitle("WiFi Usage Monitor");
        setSize(420, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new Color(28, 28, 30));

        JLabel wifi = new JLabel("📶");
        wifi.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        wifi.setBounds(180, 15, 60, 50);
        panel.add(wifi);

        JLabel title = new JLabel("WiFi Usage Monitor");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setBounds(100, 60, 250, 30);
        panel.add(title);

        JLabel user = new JLabel("Username");
        user.setForeground(Color.LIGHT_GRAY);
        user.setBounds(55, 110, 100, 20);
        panel.add(user);

        txtUser = new JTextField();
        txtUser.setBounds(55, 135, 300, 35);
        txtUser.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        txtUser.setBackground(new Color(50, 50, 50));
        txtUser.setForeground(Color.WHITE);
        txtUser.setCaretColor(Color.WHITE);
        panel.add(txtUser);

        JLabel pass = new JLabel("Password");
        pass.setForeground(Color.LIGHT_GRAY);
        pass.setBounds(55, 175, 100, 20);
        panel.add(pass);

        txtPass = new JPasswordField();
        txtPass.setBounds(55, 200, 300, 35);
        txtPass.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        txtPass.setBackground(new Color(50, 50, 50));
        txtPass.setForeground(Color.WHITE);
        txtPass.setCaretColor(Color.WHITE);
        panel.add(txtPass);

        btnLogin = new JButton("Login");
        btnLogin.setBounds(55, 250, 140, 40);
        btnLogin.setBackground(new Color(0, 120, 215));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFocusPainted(false);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.addActionListener(this);
        panel.add(btnLogin);

        btnClear = new JButton("Clear");
        btnClear.setBounds(215, 250, 140, 40);
        btnClear.setBackground(new Color(60, 60, 60));
        btnClear.setForeground(Color.WHITE);
        btnClear.setFocusPainted(false);
        btnClear.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnClear.addActionListener(this);
        panel.add(btnClear);

        add(panel);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == btnLogin) {
            String username = txtUser.getText();
            String password = String.valueOf(txtPass.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter both Username and Password.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (authenticateUser(username, password)) {
                JOptionPane.showMessageDialog(this,
                        "Welcome, " + username + "!",
                        "Login Successful",
                        JOptionPane.INFORMATION_MESSAGE);
                this.dispose();
                // Open the dashboard
                SwingUtilities.invokeLater(() -> new MainDashboard(username));
            } else {
                JOptionPane.showMessageDialog(this,
                        "Invalid Username or Password",
                        "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        if (e.getSource() == btnClear) {
            txtUser.setText("");
            txtPass.setText("");
        }
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
        } catch (Exception e) {
            System.out.println("Login Database Error: " + e.getMessage());
        }
        return false;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginPage::new);
    }
}