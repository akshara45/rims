package com.rental.payment;

import com.rental.booking.Booking;
import com.rental.booking.BookingDAO;
import com.rental.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentDAO {
    private final BookingDAO bookingDAO = new BookingDAO();

    public boolean recordPayment(Payment payment) {
        try (Connection conn = DBConnection.getConnection()) {
            return recordPaymentInTransaction(conn, payment);
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error recording payment: " + e.getMessage());
            return false;
        }
    }

    public static boolean recordPaymentInTransaction(Connection conn, Payment payment) throws SQLException {
        String sql = "INSERT INTO payments (payment_id, rental_id, amount, method, payment_date, status, payment_type, demo_transaction_ref, amount_paise) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, payment.getPaymentId());
            pstmt.setString(2, payment.getBooking().getBookingId());
            pstmt.setDouble(3, payment.getAmount());
            pstmt.setString(4, payment.getMethod());
            pstmt.setString(5, payment.getPaymentDate().toString());
            pstmt.setString(6, payment.getStatus());
            pstmt.setString(7, payment.getPaymentType());
            pstmt.setString(8, payment.getDemoTransactionRef());
            pstmt.setLong(9, Math.round(payment.getAmount() * 100));
            return pstmt.executeUpdate() > 0;
        }
    }

    public Payment getPaymentByRentalId(String rentalId) {
        String sql = "SELECT * FROM payments WHERE rental_id = ? AND payment_type = 'RENTAL' ORDER BY payment_date DESC, payment_id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rentalId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPayment(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error fetching payment by rental ID: " + e.getMessage());
        }
        return null;
    }

    public Payment getLateFeePayment(String rentalId) {
        String sql = "SELECT * FROM payments WHERE rental_id = ? AND payment_type = 'LATE_FEE' ORDER BY payment_date DESC, payment_id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rentalId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? mapResultSetToPayment(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error fetching late-fee payment: " + e.getMessage());
            return null;
        }
    }

    public boolean settleLateFee(String rentalId, String method) {
        String demoRef = "DEMO-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement stmt = conn.prepareStatement("""
                        UPDATE payments SET status='PAID', method=?, payment_date=?, demo_transaction_ref=?
                        WHERE rental_id=? AND payment_type='LATE_FEE' AND status='PENDING'
                        """)) {
                    stmt.setString(1, method);
                    stmt.setString(2, LocalDate.now().toString());
                    stmt.setString(3, demoRef);
                    stmt.setString(4, rentalId);
                    changed = stmt.executeUpdate();
                }
                if (changed == 0) { conn.rollback(); return false; }
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE late_fees SET status='PAID' WHERE rental_id=?")) {
                    stmt.setString(1, rentalId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE rentals SET late_fee_status='PAID' WHERE rental_id=?")) {
                    stmt.setString(1, rentalId);
                    stmt.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[PaymentDAO] Late fee payment rolled back: " + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error settling late-fee payment: " + e.getMessage());
            return false;
        }
    }

    public List<Payment> getAllPayments() {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY payment_date DESC, payment_id DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Payment p = mapResultSetToPayment(rs);
                if (p != null) list.add(p);
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error fetching payments: " + e.getMessage());
        }
        return list;
    }

    public double getTotalRevenue() {
        String sql = "SELECT COALESCE(SUM(CASE WHEN amount_paise > 0 THEN amount_paise ELSE CAST(ROUND(amount * 100) AS INTEGER) END), 0) FROM payments WHERE status = 'PAID'";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1) / 100.0;
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error calculating total revenue: " + e.getMessage());
        }
        return 0.0;
    }

    public Map<String, Object> getFinancialSummary() {
        Map<String, Object> summary = new HashMap<>();
        String sql = """
            SELECT
              SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) AS pending_payments,
              SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed_payments,
              COALESCE((SELECT SUM(amount_paise) FROM late_fees WHERE status='PENDING'), 0) AS late_fees_due_paise
            FROM payments
        """;
        try (Connection conn = DBConnection.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                summary.put("pendingPayments", rs.getInt("pending_payments"));
                summary.put("failedPayments", rs.getInt("failed_payments"));
                summary.put("lateFeesDue", rs.getLong("late_fees_due_paise") / 100.0);
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error reading payment summary: " + e.getMessage());
        }
        return summary;
    }

    private Payment mapResultSetToPayment(ResultSet rs) throws SQLException {
        String rentalId = rs.getString("rental_id");
        Booking booking = bookingDAO.getBookingById(rentalId);
        LocalDate date = LocalDate.parse(rs.getString("payment_date"));

        return new Payment(
            rs.getString("payment_id"),
            booking,
            rs.getDouble("amount"),
            rs.getString("method"),
            date,
            rs.getString("status"),
            rs.getString("payment_type"),
            rs.getString("demo_transaction_ref")
        );
    }
}
