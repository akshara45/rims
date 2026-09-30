package com.rental.user;

import com.rental.util.DBConnection;
import com.rental.util.SecurityUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDAO {

    public boolean registerCustomer(User user, String plainPassword) {
        // Enforce: Customers ONLY. No admin registration allowed!
        String sql = "INSERT INTO users (user_id, name, email, password, role, phone, created_at) VALUES (?, ?, ?, ?, 'CUSTOMER', ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getUserId());
            pstmt.setString(2, user.getName());
            pstmt.setString(3, user.getEmail().trim().toLowerCase());
            pstmt.setString(4, SecurityUtil.hashPassword(plainPassword));
            pstmt.setString(5, user.getPhone());
            pstmt.setString(6, LocalDate.now().toString());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error registering customer: " + e.getMessage());
            return false;
        }
    }

    public User authenticate(String email, String plainPassword) {
        if (email == null || plainPassword == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    if (SecurityUtil.verifyPassword(plainPassword, storedHash)) {
                        return mapResultSetToUser(rs);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error during authentication: " + e.getMessage());
        }
        return null;
    }

    public User getUserById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error fetching user by ID: " + e.getMessage());
        }
        return null;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error fetching user by email: " + e.getMessage());
        }
        return null;
    }

    public boolean updateProfile(String userId, String name, String phone) {
        String sql = "UPDATE users SET name = ?, phone = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, phone);
            pstmt.setString(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error updating profile: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getAllCustomersWithStats() {
        List<Map<String, Object>> customers = new ArrayList<>();
        String sql = """
            SELECT u.user_id, u.name, u.email, u.phone, u.created_at,
                   COUNT(r.rental_id) AS total_rentals,
                   SUM(CASE WHEN r.status IN ('ACTIVE', 'APPROVED') THEN 1 ELSE 0 END) AS active_rentals,
                   COALESCE(SUM(CASE WHEN r.status != 'CANCELLED' AND r.status != 'REJECTED' THEN r.total_amount ELSE 0 END), 0) AS total_spent
            FROM users u
            LEFT JOIN rentals r ON u.user_id = r.customer_id
            WHERE u.role = 'CUSTOMER'
            GROUP BY u.user_id, u.name, u.email, u.phone, u.created_at
            ORDER BY u.created_at DESC, u.name ASC;
        """;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("customerId", rs.getString("user_id"));
                map.put("name", rs.getString("name"));
                map.put("email", rs.getString("email"));
                map.put("phone", rs.getString("phone") != null ? rs.getString("phone") : "—");
                map.put("createdAt", rs.getString("created_at"));
                map.put("totalRentals", rs.getInt("total_rentals"));
                map.put("activeRentals", rs.getInt("active_rentals"));
                map.put("totalSpent", rs.getDouble("total_spent"));
                customers.add(map);
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error fetching customers with stats: " + e.getMessage());
        }
        return customers;
    }

    public int getTotalCustomerCount() {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER'";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[UserDAO] Error counting customers: " + e.getMessage());
        }
        return 0;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        return new User(
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("phone"),
            rs.getString("password"),
            rs.getString("created_at")
        );
    }
}
