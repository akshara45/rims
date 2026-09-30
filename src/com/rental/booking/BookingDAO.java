package com.rental.booking;

import com.rental.item.Item;
import com.rental.item.ItemDAO;
import com.rental.user.User;
import com.rental.user.UserDAO;
import com.rental.util.DBConnection;

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

public class BookingDAO {
    private final UserDAO userDAO = new UserDAO();
    private final ItemDAO itemDAO = new ItemDAO();

    public synchronized boolean createBooking(Booking booking) {
        String checkAvailabilitySql = "SELECT available, rental_price FROM items WHERE item_id = ?";
        String insertSql = "INSERT INTO rentals (rental_id, customer_id, item_id, start_date, end_date, number_of_days, daily_rate, total_amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateItemSql = "UPDATE items SET available = 0 WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Strict server-side availability check
            try (PreparedStatement checkStmt = conn.prepareStatement(checkAvailabilitySql)) {
                checkStmt.setString(1, booking.getItem().getItemId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next() || rs.getInt("available") != 1) {
                        conn.rollback();
                        System.err.println("[BookingDAO] Item is not available for rental.");
                        return false;
                    }
                    // Server calculates rate and total to prevent frontend manipulation
                    double rate = rs.getDouble("rental_price");
                    booking.setDailyRate(rate);
                    booking.setTotalAmount(booking.getNumberOfDays() * rate);
                }
            }

            // 2. Insert rental record
            try (PreparedStatement pstmtRental = conn.prepareStatement(insertSql);
                 PreparedStatement pstmtItem = conn.prepareStatement(updateItemSql)) {

                pstmtRental.setString(1, booking.getBookingId());
                pstmtRental.setString(2, booking.getUser().getUserId());
                pstmtRental.setString(3, booking.getItem().getItemId());
                pstmtRental.setString(4, booking.getStartDate().toString());
                pstmtRental.setString(5, booking.getEndDate().toString());
                pstmtRental.setInt(6, booking.getNumberOfDays());
                pstmtRental.setDouble(7, booking.getDailyRate());
                pstmtRental.setDouble(8, booking.getTotalAmount());
                pstmtRental.setString(9, booking.getStatus());
                pstmtRental.setString(10, booking.getCreatedAt().toString());
                pstmtRental.executeUpdate();

                // 3. Mark item unavailable
                pstmtItem.setString(1, booking.getItem().getItemId());
                pstmtItem.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[BookingDAO] Rollback during rental creation: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Connection error: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean updateStatus(String rentalId, String newStatus) {
        String getRentalSql = "SELECT item_id, status FROM rentals WHERE rental_id = ?";
        String updateRentalSql = "UPDATE rentals SET status = ?, returned_at = ? WHERE rental_id = ?";
        String updateItemSql = "UPDATE items SET available = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            String itemId = null;
            String currentStatus = null;

            try (PreparedStatement stmt = conn.prepareStatement(getRentalSql)) {
                stmt.setString(1, rentalId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        itemId = rs.getString("item_id");
                        currentStatus = rs.getString("status");
                    }
                }
            }

            if (itemId == null) {
                conn.rollback();
                return false;
            }

            String upperStatus = newStatus.toUpperCase();
            String returnedDate = upperStatus.equals("RETURNED") ? LocalDate.now().toString() : null;

            try (PreparedStatement stmtRental = conn.prepareStatement(updateRentalSql);
                 PreparedStatement stmtItem = conn.prepareStatement(updateItemSql)) {

                stmtRental.setString(1, upperStatus);
                stmtRental.setString(2, returnedDate);
                stmtRental.setString(3, rentalId);
                stmtRental.executeUpdate();

                // Determine if item should be available again
                int availableStatus;
                if (upperStatus.equals("RETURNED") || upperStatus.equals("CANCELLED") || upperStatus.equals("REJECTED")) {
                    availableStatus = 1;
                } else {
                    availableStatus = 0;
                }

                stmtItem.setInt(1, availableStatus);
                stmtItem.setString(2, itemId);
                stmtItem.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[BookingDAO] Rollback during status update: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Connection error: " + e.getMessage());
            return false;
        }
    }

    public boolean returnBooking(String bookingId) {
        return updateStatus(bookingId, "RETURNED");
    }

    public boolean cancelBooking(String bookingId) {
        return updateStatus(bookingId, "CANCELLED");
    }

    public Booking getBookingById(String rentalId) {
        String sql = "SELECT * FROM rentals WHERE rental_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rentalId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToBooking(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Error fetching rental by ID: " + e.getMessage());
        }
        return null;
    }

    public List<Booking> getBookingsByUser(String customerId) {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT * FROM rentals WHERE customer_id = ? ORDER BY created_at DESC, rental_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, customerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Booking b = mapResultSetToBooking(rs);
                    if (b != null) list.add(b);
                }
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Error fetching customer rentals: " + e.getMessage());
        }
        return list;
    }

    public List<Booking> getAllBookings() {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT * FROM rentals ORDER BY created_at DESC, rental_id DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Booking b = mapResultSetToBooking(rs);
                if (b != null) list.add(b);
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Error fetching all rentals: " + e.getMessage());
        }
        return list;
    }

    public Map<String, Object> getRentalAnalytics() {
        Map<String, Object> map = new HashMap<>();
        String sql = """
            SELECT 
                COUNT(*) AS total_rentals,
                SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_rentals,
                SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_rentals,
                SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_rentals,
                SUM(CASE WHEN status = 'RETURNED' THEN 1 ELSE 0 END) AS returned_rentals,
                SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_rentals,
                SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_rentals,
                COALESCE(SUM(CASE WHEN status NOT IN ('CANCELLED', 'REJECTED') THEN total_amount ELSE 0 END), 0.0) AS total_revenue
            FROM rentals;
        """;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                map.put("totalRentals", rs.getInt("total_rentals"));
                map.put("activeRentals", rs.getInt("active_rentals"));
                map.put("approvedRentals", rs.getInt("approved_rentals"));
                map.put("pendingRentals", rs.getInt("pending_rentals"));
                map.put("returnedRentals", rs.getInt("returned_rentals"));
                map.put("cancelledRentals", rs.getInt("cancelled_rentals"));
                map.put("rejectedRentals", rs.getInt("rejected_rentals"));
                map.put("totalRevenue", rs.getDouble("total_revenue"));
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Error getting rental analytics: " + e.getMessage());
        }
        return map;
    }

    private Booking mapResultSetToBooking(ResultSet rs) throws SQLException {
        String customerId = rs.getString("customer_id");
        String itemId = rs.getString("item_id");
        User user = userDAO.getUserById(customerId);
        Item item = itemDAO.getItemById(itemId);

        LocalDate start = LocalDate.parse(rs.getString("start_date"));
        LocalDate end = LocalDate.parse(rs.getString("end_date"));
        LocalDate createdAt = LocalDate.parse(rs.getString("created_at"));
        String returnedAtStr = rs.getString("returned_at");
        LocalDate returnedAt = (returnedAtStr != null && !returnedAtStr.isEmpty()) ? LocalDate.parse(returnedAtStr) : null;

        return new Booking(
            rs.getString("rental_id"),
            user,
            item,
            start,
            end,
            rs.getInt("number_of_days"),
            rs.getDouble("daily_rate"),
            rs.getDouble("total_amount"),
            rs.getString("status"),
            createdAt,
            returnedAt
        );
    }
}
