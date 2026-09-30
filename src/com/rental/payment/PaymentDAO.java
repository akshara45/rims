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
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {
    private final BookingDAO bookingDAO = new BookingDAO();

    public boolean recordPayment(Payment payment) {
        String sql = "INSERT INTO payments (payment_id, rental_id, amount, method, payment_date, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, payment.getPaymentId());
            pstmt.setString(2, payment.getBooking().getBookingId());
            pstmt.setDouble(3, payment.getAmount());
            pstmt.setString(4, payment.getMethod());
            pstmt.setString(5, payment.getPaymentDate().toString());
            pstmt.setString(6, payment.getStatus());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error recording payment: " + e.getMessage());
            return false;
        }
    }

    public Payment getPaymentByRentalId(String rentalId) {
        String sql = "SELECT * FROM payments WHERE rental_id = ?";
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
        String sql = "SELECT SUM(amount) FROM payments WHERE status = 'PAID'";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.err.println("[PaymentDAO] Error calculating total revenue: " + e.getMessage());
        }
        return 0.0;
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
            rs.getString("status")
        );
    }
}
