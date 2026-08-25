package com.rental.payment;

import com.rental.booking.Booking;
import java.time.LocalDate;

public class Payment {
    private String paymentId;
    private Booking booking;
    private double amount;
    private String method;
    private LocalDate paymentDate;
    private String status; // "PAID", "PENDING"

    public Payment(String paymentId, Booking booking, double amount, String method) {
        this.paymentId = paymentId;
        this.booking = booking;
        this.amount = amount;
        this.method = method;
        this.paymentDate = LocalDate.now();
        this.status = "PAID";
    }

    public String getPaymentId() { return paymentId; }
    public Booking getBooking() { return booking; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return "Payment{id=" + paymentId + ", amount=" + amount + ", method=" + method + ", status=" + status + "}";
    }
}