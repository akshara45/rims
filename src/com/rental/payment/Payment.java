package com.rental.payment;

import com.rental.booking.Booking;
import java.time.LocalDate;

public class Payment {
    private String paymentId;
    private Booking booking;
    private double amount;
    private String method; // "CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "CASH"
    private LocalDate paymentDate;
    private String status; // "PAID", "PENDING", "REFUNDED"
    private String paymentType;
    private String demoTransactionRef;

    public Payment(String paymentId, Booking booking, double amount, String method) {
        this(paymentId, booking, amount, method, LocalDate.now(), "PAID");
    }

    public Payment(String paymentId, Booking booking, double amount, String method, LocalDate paymentDate, String status) {
        this(paymentId, booking, amount, method, paymentDate, status, "RENTAL", null);
    }

    public Payment(String paymentId, Booking booking, double amount, String method, LocalDate paymentDate,
                   String status, String paymentType, String demoTransactionRef) {
        this.paymentId = paymentId;
        this.booking = booking;
        this.amount = amount;
        this.method = (method != null && !method.trim().isEmpty()) ? method : "CREDIT_CARD";
        this.paymentDate = paymentDate != null ? paymentDate : LocalDate.now();
        this.status = (status != null && !status.trim().isEmpty()) ? status : "PAID";
        this.paymentType = (paymentType != null && !paymentType.trim().isEmpty()) ? paymentType : "RENTAL";
        this.demoTransactionRef = demoTransactionRef;
    }

    public String getPaymentId() { return paymentId; }
    public Booking getBooking() { return booking; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public String getStatus() { return status; }
    public String getPaymentType() { return paymentType; }
    public String getDemoTransactionRef() { return demoTransactionRef; }

    public void setAmount(double amount) { this.amount = amount; }
    public void setStatus(String status) { this.status = status; }
    public void setMethod(String method) { this.method = method; }

    @Override
    public String toString() {
        return "Payment{id=" + paymentId + ", booking=" + (booking != null ? booking.getBookingId() : "N/A") 
             + ", amount=" + amount + ", method=" + method + ", status=" + status + ", date=" + paymentDate + "}";
    }
}
