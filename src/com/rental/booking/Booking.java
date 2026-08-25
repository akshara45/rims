package com.rental.booking;

import com.rental.user.User;
import com.rental.item.Item;
import java.time.LocalDate;

public class Booking {
    private String bookingId;
    private User user;
    private Item item;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // "ACTIVE", "RETURNED", "CANCELLED"

    public Booking(String bookingId, User user, Item item, LocalDate startDate, LocalDate endDate) {
        this.bookingId = bookingId;
        this.user = user;
        this.item = item;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = "ACTIVE";
    }

    public String getBookingId() { return bookingId; }
    public User getUser() { return user; }
    public Item getItem() { return item; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "Booking{id=" + bookingId + ", user=" + user.getName() + ", item=" + item.getName() 
               + ", status=" + status + "}";
    }
}