package com.rental.report;

import com.rental.booking.Booking;
import com.rental.booking.BookingDAO;
import com.rental.item.Item;
import com.rental.item.ItemDAO;
import com.rental.user.UserDAO;
import com.rental.payment.PaymentDAO;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {
    private final ItemDAO itemDAO = new ItemDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final UserDAO userDAO = new UserDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    public Map<String, Object> getAdminAnalytics() {
        Map<String, Object> data = new HashMap<>();

        List<Item> allItems = itemDAO.getAllItems();
        List<Booking> allRentals = bookingDAO.getAllBookings();
        int customerCount = userDAO.getTotalCustomerCount();

        long availableCount = allItems.stream()
            .filter(item -> itemDAO.isAvailableForRental(item.getItemId(), LocalDate.now(), LocalDate.now()))
            .count();
        long rentedCount = allItems.size() - availableCount;

        long activeRentals = allRentals.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())
                || "CONFIRMED".equalsIgnoreCase(b.getStatus())).count();
        long pendingRentals = allRentals.stream().filter(b -> "PENDING".equalsIgnoreCase(b.getStatus())).count();
        long approvedRentals = allRentals.stream().filter(b -> "APPROVED".equalsIgnoreCase(b.getStatus())).count();
        long returnedRentals = allRentals.stream().filter(b -> "RETURNED".equalsIgnoreCase(b.getStatus())).count();
        long cancelledRentals = allRentals.stream().filter(b -> "CANCELLED".equalsIgnoreCase(b.getStatus()) || "REJECTED".equalsIgnoreCase(b.getStatus())).count();

        double totalRevenue = paymentDAO.getTotalRevenue();

        // 1. KPI Stats
        data.put("totalItems", allItems.size());
        data.put("availableItems", availableCount);
        data.put("rentedItems", rentedCount);
        data.put("totalCustomers", customerCount);
        data.put("totalRentals", allRentals.size());
        data.put("activeRentals", activeRentals + approvedRentals);
        data.put("pendingRentals", pendingRentals);
        data.put("returnedRentals", returnedRentals);
        data.put("cancelledRentals", cancelledRentals);
        data.put("totalRevenue", totalRevenue);
        data.putAll(paymentDAO.getFinancialSummary());

        // 2. Category Breakdown
        Map<String, Map<String, Object>> categoryStats = new LinkedHashMap<>();
        for (Item item : allItems) {
            String cat = item.getCategory();
            categoryStats.putIfAbsent(cat, new HashMap<>(Map.of("itemCount", 0, "rentalCount", 0, "revenue", 0.0)));
            Map<String, Object> catMap = categoryStats.get(cat);
            catMap.put("itemCount", (int) catMap.get("itemCount") + 1);
        }

        for (Booking b : allRentals) {
            if (b.getItem() != null) {
                String cat = b.getItem().getCategory();
                if (categoryStats.containsKey(cat)) {
                    Map<String, Object> catMap = categoryStats.get(cat);
                    catMap.put("rentalCount", (int) catMap.get("rentalCount") + 1);
                    if (!"CANCELLED".equalsIgnoreCase(b.getStatus()) && !"REJECTED".equalsIgnoreCase(b.getStatus())) {
                        double paidRental = "PAID".equalsIgnoreCase(b.getPaymentStatus()) ? b.getTotalAmount() : 0.0;
                        double paidLateFee = "PAID".equalsIgnoreCase(b.getLateFeeStatus()) ? b.getLateFee() : 0.0;
                        catMap.put("revenue", (double) catMap.get("revenue") + paidRental + paidLateFee);
                    }
                }
            }
        }
        data.put("categoryStats", categoryStats);

        // 3. Most Rented Items
        Map<String, Long> itemRentalCounts = allRentals.stream()
            .filter(b -> b.getItem() != null)
            .collect(Collectors.groupingBy(b -> b.getItem().getName(), Collectors.counting()));

        List<Map<String, Object>> topItems = itemRentalCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", entry.getKey());
                m.put("count", entry.getValue());
                return m;
            })
            .collect(Collectors.toList());

        data.put("topItems", topItems);

        return data;
    }

    public String generateConsoleReport() {
        Map<String, Object> stats = getAdminAnalytics();
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("       ITEM RENTAL MANAGEMENT SYSTEM (RIMS)         \n");
        sb.append("                 Date: ").append(LocalDate.now()).append("\n");
        sb.append("====================================================\n");
        sb.append(String.format("Total Inventory Items: %s\n", stats.get("totalItems")));
        sb.append(String.format("  - Available for Rent: %s\n", stats.get("availableItems")));
        sb.append(String.format("  - Currently Rented:   %s\n", stats.get("rentedItems")));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("Total Registered Customers: %s\n", stats.get("totalCustomers")));
        sb.append(String.format("Total Rentals Processed:    %s\n", stats.get("totalRentals")));
        sb.append(String.format("  - Active Rentals:         %s\n", stats.get("activeRentals")));
        sb.append(String.format("  - Returned Rentals:       %s\n", stats.get("returnedRentals")));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format(Locale.US, "Total Revenue:             ₹%.2f\n", (Double) stats.get("totalRevenue")));
        sb.append("====================================================\n");
        return sb.toString();
    }
}
