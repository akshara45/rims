package com.rental.booking;

import com.rental.item.Item;
import com.rental.item.ItemDAO;
import com.rental.user.User;
import com.rental.user.UserDAO;
import com.rental.payment.Payment;
import com.rental.payment.PaymentDAO;
import com.rental.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BookingDAO {
    private final UserDAO userDAO = new UserDAO();
    private final ItemDAO itemDAO = new ItemDAO();

    public synchronized boolean createBooking(Booking booking) {
        return createBooking(booking, null);
    }

    public synchronized boolean createBooking(Booking booking, Payment payment) {
        String checkAvailabilitySql = "SELECT rental_price_paise, rental_price, available FROM items WHERE item_id = ? FOR UPDATE";
        String overlapSql = """
            SELECT COUNT(*) FROM rentals
            WHERE item_id = ? AND status NOT IN ('RETURNED','CANCELLED','REJECTED')
              AND start_date < ?
              AND (end_date > ? OR (end_date = start_date AND start_date >= ?))
        """;
        String insertSql = """
            INSERT INTO rentals (rental_id, customer_id, item_id, start_date, end_date, number_of_days,
              daily_rate, total_amount, status, created_at, payment_status, payment_method, late_fee,
              total_due, late_fee_status, daily_rate_paise, total_amount_paise)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'NOT_DUE', ?, ?)
        """;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long ratePaise;
                try (PreparedStatement stmt = conn.prepareStatement(checkAvailabilitySql)) {
                    stmt.setString(1, booking.getItem().getItemId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next() || rs.getInt("available") != 1) {
                            conn.rollback();
                            return false;
                        }
                        ratePaise = rs.getLong("rental_price_paise");
                        if (ratePaise <= 0) ratePaise = Math.round(rs.getDouble("rental_price") * 100);
                    }
                }
                LocalDate start = booking.getStartDate();
                LocalDate end = booking.getEndDate();
                String endExclusive = end.isAfter(start) ? end.toString() : start.plusDays(1).toString();
                try (PreparedStatement stmt = conn.prepareStatement(overlapSql)) {
                    stmt.setString(1, booking.getItem().getItemId());
                    stmt.setString(2, endExclusive);
                    stmt.setString(3, start.toString());
                    stmt.setString(4, start.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                int days = Booking.calculateDays(start, end);
                double rate = ratePaise / 100.0;
                double total = (ratePaise * (long) days) / 100.0;
                booking.setNumberOfDays(days);
                booking.setDailyRate(rate);
                booking.setTotalAmount(total);
                booking.setTotalDue(total);
                if (payment != null) {
                    payment.setAmount(total);
                    booking.setPaymentStatus(payment.getStatus());
                    booking.setPaymentMethod(payment.getMethod());
                }

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, booking.getBookingId());
                    stmt.setString(2, booking.getUser().getUserId());
                    stmt.setString(3, booking.getItem().getItemId());
                    stmt.setString(4, start.toString());
                    stmt.setString(5, end.toString());
                    stmt.setInt(6, days);
                    stmt.setDouble(7, rate);
                    stmt.setDouble(8, total);
                    stmt.setString(9, booking.getStatus());
                    stmt.setString(10, booking.getCreatedAt().toString());
                    stmt.setString(11, booking.getPaymentStatus());
                    stmt.setString(12, booking.getPaymentMethod());
                    stmt.setDouble(13, total);
                    stmt.setLong(14, ratePaise);
                    stmt.setLong(15, ratePaise * days);
                    stmt.executeUpdate();
                }
                if (payment != null) PaymentDAO.recordPaymentInTransaction(conn, payment);
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
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
        String status = newStatus == null ? "" : newStatus.toUpperCase(Locale.ROOT);
        if ("RETURNED".equals(status)) return processReturn(rentalId, null, LocalDate.now());
        if (!Set.of("CONFIRMED", "ACTIVE", "APPROVED", "CANCELLED", "REJECTED").contains(status)) return false;
        String sql = "UPDATE rentals SET status = ? WHERE rental_id = ? AND status NOT IN ('RETURNED','CANCELLED','REJECTED')";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, rentalId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Error updating rental status: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean processReturn(String rentalId, String processedBy, LocalDate actualReturnDate) {
        String getSql = "SELECT end_date, daily_rate_paise, daily_rate, status, total_amount FROM rentals WHERE rental_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDate endDate;
                long ratePaise;
                String status;
                double rentalAmount;
                try (PreparedStatement stmt = conn.prepareStatement(getSql)) {
                    stmt.setString(1, rentalId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        endDate = LocalDate.parse(rs.getString("end_date"));
                        ratePaise = rs.getLong("daily_rate_paise");
                        if (ratePaise <= 0) ratePaise = Math.round(rs.getDouble("daily_rate") * 100);
                        status = rs.getString("status");
                        rentalAmount = rs.getDouble("total_amount");
                    }
                }
                if ("RETURNED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
                    conn.rollback(); return false;
                }
                LocalDate returned = actualReturnDate == null ? LocalDate.now() : actualReturnDate;
                if (returned.isAfter(LocalDate.now())) { conn.rollback(); return false; }
                int daysLate = (int) Math.max(0, ChronoUnit.DAYS.between(endDate, returned));
                long feeRatePaise = Math.round(ratePaise * 1.5d);
                long feePaise = feeRatePaise * daysLate;
                double fee = feePaise / 100.0;
                String feeStatus = feePaise > 0 ? "PENDING" : "NOT_DUE";

                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO returns (return_id, rental_id, actual_return_date, days_late, processed_by, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                    stmt.setString(1, "RET-" + UUID.randomUUID());
                    stmt.setString(2, rentalId);
                    stmt.setString(3, returned.toString());
                    stmt.setInt(4, daysLate);
                    stmt.setString(5, processedBy);
                    stmt.setString(6, LocalDate.now().toString());
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO late_fees (late_fee_id, rental_id, days_late, rate_per_day_paise, amount_paise, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    stmt.setString(1, "LFE-" + UUID.randomUUID());
                    stmt.setString(2, rentalId);
                    stmt.setInt(3, daysLate);
                    stmt.setLong(4, feeRatePaise);
                    stmt.setLong(5, feePaise);
                    stmt.setString(6, feeStatus);
                    stmt.setString(7, LocalDate.now().toString());
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE rentals SET status='RETURNED', returned_at=?, late_days=?, late_fee=?, total_due=?, late_fee_status=? WHERE rental_id=?")) {
                    stmt.setString(1, returned.toString());
                    stmt.setInt(2, daysLate);
                    stmt.setDouble(3, fee);
                    stmt.setDouble(4, rentalAmount + fee);
                    stmt.setString(5, feeStatus);
                    stmt.setString(6, rentalId);
                    stmt.executeUpdate();
                }
                if (feePaise > 0) {
                    Booking paymentBooking = new Booking(rentalId, null, null, endDate, endDate,
                            1, ratePaise / 100.0, rentalAmount, "RETURNED", LocalDate.now(), returned);
                    Payment feePayment = new Payment("PAY-" + UUID.randomUUID(), paymentBooking, fee,
                            "CASH_ON_PICKUP", LocalDate.now(), "PENDING", "LATE_FEE", null);
                    PaymentDAO.recordPaymentInTransaction(conn, feePayment);
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                System.err.println("[BookingDAO] Return transaction rolled back: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[BookingDAO] Error processing return: " + e.getMessage());
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
                SUM(CASE WHEN status IN ('PENDING','CONFIRMED') THEN 1 ELSE 0 END) AS pending_rentals,
                SUM(CASE WHEN status = 'RETURNED' THEN 1 ELSE 0 END) AS returned_rentals,
                SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_rentals,
                SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_rentals,
                (SELECT COALESCE(SUM(amount), 0.0) FROM payments WHERE status = 'PAID' AND payment_type = 'RENTAL') AS total_revenue
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

        Booking booking = new Booking(
            rs.getString("rental_id"),
            user,
            item,
            start,
            end,
            rs.getInt("number_of_days"),
            rs.getLong("daily_rate_paise") > 0 ? rs.getLong("daily_rate_paise") / 100.0 : rs.getDouble("daily_rate"),
            rs.getLong("total_amount_paise") > 0 ? rs.getLong("total_amount_paise") / 100.0 : rs.getDouble("total_amount"),
            rs.getString("status"),
            createdAt,
            returnedAt
        );
        booking.setPaymentStatus(rs.getString("payment_status"));
        booking.setPaymentMethod(rs.getString("payment_method"));
        booking.setLateFee(rs.getDouble("late_fee"));
        booking.setTotalDue(rs.getDouble("total_due"));
        booking.setLateFeeStatus(rs.getString("late_fee_status"));
        booking.setLateDays(rs.getInt("late_days"));
        return booking;
    }
}
