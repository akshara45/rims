package com.rental.booking;

import com.rental.user.User;
import com.rental.item.Item;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Booking {
    private String bookingId;
    private User user;
    private Item item;
    private LocalDate startDate;
    private LocalDate endDate;
    private int numberOfDays;
    private double dailyRate;
    private double totalAmount;
    private String status; // "PENDING", "APPROVED", "ACTIVE", "RETURNED", "CANCELLED", "REJECTED"
    private String paymentStatus; // "PENDING", "PAID"
    private LocalDate createdAt;
    private LocalDate returnedAt;

    public Booking(String bookingId, User user, Item item, LocalDate startDate, LocalDate endDate) {
        this(bookingId, user, item, startDate, endDate, 
             calculateDays(startDate, endDate), 
             item != null ? item.getRentalPrice() : 0.0, 
             calculateCost(item, startDate, endDate), 
             "ACTIVE", 
             LocalDate.now(), 
             null);
    }

    public Booking(String bookingId, User user, Item item, LocalDate startDate, LocalDate endDate, 
                   int numberOfDays, double dailyRate, double totalAmount, String status, 
                   LocalDate createdAt, LocalDate returnedAt) {
        this.bookingId = bookingId;
        this.user = user;
        this.item = item;
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfDays = numberOfDays > 0 ? numberOfDays : calculateDays(startDate, endDate);
        this.dailyRate = dailyRate > 0 ? dailyRate : (item != null ? item.getRentalPrice() : 0.0);
        this.totalAmount = totalAmount > 0 ? totalAmount : (this.numberOfDays * this.dailyRate);
        this.status = (status != null && !status.trim().isEmpty()) ? status.toUpperCase() : "ACTIVE";
        this.createdAt = createdAt != null ? createdAt : LocalDate.now();
        this.returnedAt = returnedAt;
    }

    public static int calculateDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 1;
        long days = ChronoUnit.DAYS.between(start, end);
        return days <= 0 ? 1 : (int) days;
    }

    public static double calculateCost(Item item, LocalDate start, LocalDate end) {
        if (item == null || start == null || end == null) return 0.0;
        int days = calculateDays(start, end);
        return days * item.getRentalPrice();
    }

    public String getBookingId() { return bookingId; }
    public String getRentalId() { return bookingId; }
    public User getUser() { return user; }
    public User getCustomer() { return user; }
    public Item getItem() { return item; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getNumberOfDays() { return numberOfDays; }
    public long getRentalDays() { return numberOfDays; }
    public double getDailyRate() { return dailyRate; }
    public double getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public LocalDate getCreatedAt() { return createdAt; }
    public LocalDate getReturnedAt() { return returnedAt; }

    public void setStatus(String status) { this.status = status; }
    public String getPaymentStatus() {
        if (paymentStatus != null && !paymentStatus.trim().isEmpty()) {
            return paymentStatus;
        }
        return "PENDING".equalsIgnoreCase(status) ? "PENDING" : "PAID";
    }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public void setDailyRate(double dailyRate) { this.dailyRate = dailyRate; }
    public void setNumberOfDays(int numberOfDays) { this.numberOfDays = numberOfDays; }
    public void setReturnedAt(LocalDate returnedAt) { this.returnedAt = returnedAt; }

    @Override
    public String toString() {
        return "Rental{id=" + bookingId + ", customer=" + (user != null ? user.getName() : "N/A") 
             + ", item=" + (item != null ? item.getName() : "N/A") 
             + ", dates=" + startDate + " to " + endDate 
             + ", days=" + numberOfDays + ", rate=" + dailyRate 
             + ", total=" + totalAmount + ", status=" + status + "}";
    }
}