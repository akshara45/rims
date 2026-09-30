package com.rental.item;

import com.rental.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    public boolean addItem(Item item) {
        String sql = "INSERT INTO items (item_id, name, category, description, rental_price, available, image_url) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, item.getItemId());
            pstmt.setString(2, item.getName());
            pstmt.setString(3, item.getCategory());
            pstmt.setString(4, item.getDescription());
            pstmt.setDouble(5, item.getRentalPrice());
            pstmt.setInt(6, item.isAvailable() ? 1 : 0);
            pstmt.setString(7, item.getImageUrl());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error adding item: " + e.getMessage());
            return false;
        }
    }

    public boolean updateItem(Item item) {
        String sql = "UPDATE items SET name = ?, category = ?, description = ?, rental_price = ?, available = ?, image_url = ? WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getCategory());
            pstmt.setString(3, item.getDescription());
            pstmt.setDouble(4, item.getRentalPrice());
            pstmt.setInt(5, item.isAvailable() ? 1 : 0);
            pstmt.setString(6, item.getImageUrl());
            pstmt.setString(7, item.getItemId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error updating item: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteItem(String itemId) {
        String checkSql = "SELECT COUNT(*) FROM rentals WHERE item_id = ? AND status IN ('ACTIVE', 'APPROVED', 'PENDING')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement checkPstmt = conn.prepareStatement(checkSql)) {
            checkPstmt.setString(1, itemId);
            try (ResultSet rs = checkPstmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    System.err.println("[ItemDAO] Cannot delete item with active or pending rentals.");
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error checking rentals before item delete: " + e.getMessage());
            return false;
        }

        String sql = "DELETE FROM items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, itemId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error deleting item: " + e.getMessage());
            return false;
        }
    }

    public Item getItemById(String itemId) {
        String sql = "SELECT * FROM items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error getting item by ID: " + e.getMessage());
        }
        return null;
    }

    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items ORDER BY category, name";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error getting all items: " + e.getMessage());
        }
        return items;
    }

    public List<Item> getAvailableItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE available = 1 ORDER BY category, name";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error getting available items: " + e.getMessage());
        }
        return items;
    }

    public boolean setAvailability(String itemId, boolean available) {
        String sql = "UPDATE items SET available = ? WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, available ? 1 : 0);
            pstmt.setString(2, itemId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error updating availability: " + e.getMessage());
            return false;
        }
    }

    public List<Item> searchItems(String query, String category) {
        List<Item> items = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM items WHERE 1=1 ");
        List<String> params = new ArrayList<>();

        if (category != null && !category.equalsIgnoreCase("ALL") && !category.trim().isEmpty()) {
            sql.append("AND LOWER(category) = LOWER(?) ");
            params.add(category.trim());
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (LOWER(name) LIKE LOWER(?) OR LOWER(description) LIKE LOWER(?)) ");
            String wild = "%" + query.trim() + "%";
            params.add(wild);
            params.add(wild);
        }
        sql.append("ORDER BY available DESC, category, name");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapResultSetToItem(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] Error searching items: " + e.getMessage());
        }
        return items;
    }

    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        return new Item(
            rs.getString("item_id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getDouble("rental_price"),
            rs.getInt("available") == 1,
            rs.getString("image_url")
        );
    }
}
