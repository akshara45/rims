package com.rental.util;

import com.rental.booking.Booking;
import com.rental.item.Item;
import com.rental.payment.Payment;
import com.rental.user.User;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtil {

    public static String escape(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String userToJson(User user) {
        if (user == null) return "null";
        return String.format(Locale.US,
            "{\"userId\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"phone\":\"%s\",\"createdAt\":\"%s\"}",
            escape(user.getUserId()),
            escape(user.getName()),
            escape(user.getEmail()),
            escape(user.getRole()),
            escape(user.getPhone()),
            escape(user.getCreatedAt())
        );
    }

    public static String itemToJson(Item item) {
        if (item == null) return "null";
        return String.format(Locale.US,
            "{\"itemId\":\"%s\",\"name\":\"%s\",\"category\":\"%s\",\"description\":\"%s\",\"rentalPrice\":%.2f,\"available\":%b,\"imageUrl\":\"%s\"}",
            escape(item.getItemId()),
            escape(item.getName()),
            escape(item.getCategory()),
            escape(item.getDescription()),
            item.getRentalPrice(),
            item.isAvailable(),
            escape(item.getImageUrl())
        );
    }

    public static String bookingToJson(Booking booking) {
        if (booking == null) return "null";
        String returnedAtStr = booking.getReturnedAt() != null ? "\"" + booking.getReturnedAt().toString() + "\"" : "null";
        return String.format(Locale.US,
            "{\"rentalId\":\"%s\",\"bookingId\":\"%s\",\"customer\":%s,\"user\":%s,\"item\":%s,\"startDate\":\"%s\",\"endDate\":\"%s\",\"numberOfDays\":%d,\"rentalDays\":%d,\"dailyRate\":%.2f,\"totalAmount\":%.2f,\"lateDays\":%d,\"lateFee\":%.2f,\"totalDue\":%.2f,\"lateFeeStatus\":\"%s\",\"status\":\"%s\",\"paymentStatus\":\"%s\",\"paymentMethod\":\"%s\",\"createdAt\":\"%s\",\"returnedAt\":%s}",
            escape(booking.getBookingId()),
            escape(booking.getBookingId()),
            userToJson(booking.getUser()),
            userToJson(booking.getUser()),
            itemToJson(booking.getItem()),
            booking.getStartDate() != null ? booking.getStartDate().toString() : "",
            booking.getEndDate() != null ? booking.getEndDate().toString() : "",
            booking.getNumberOfDays(),
            booking.getNumberOfDays(),
            booking.getDailyRate(),
            booking.getTotalAmount(),
            booking.getLateDays(),
            booking.getLateFee(),
            booking.getTotalDue(),
            escape(booking.getLateFeeStatus()),
            escape(booking.getStatus()),
            escape(booking.getPaymentStatus()),
            escape(booking.getPaymentMethod()),
            booking.getCreatedAt() != null ? booking.getCreatedAt().toString() : "",
            returnedAtStr
        );
    }

    public static String paymentToJson(Payment payment) {
        if (payment == null) return "null";
        String rentalId = payment.getBooking() != null ? payment.getBooking().getBookingId() : "";
        return String.format(Locale.US,
            "{\"paymentId\":\"%s\",\"rentalId\":\"%s\",\"amount\":%.2f,\"method\":\"%s\",\"paymentDate\":\"%s\",\"status\":\"%s\",\"paymentType\":\"%s\",\"demoTransactionRef\":%s}",
            escape(payment.getPaymentId()),
            escape(rentalId),
            payment.getAmount(),
            escape(payment.getMethod()),
            payment.getPaymentDate() != null ? payment.getPaymentDate().toString() : "",
            escape(payment.getStatus()),
            escape(payment.getPaymentType()),
            payment.getDemoTransactionRef() == null ? "null" : "\"" + escape(payment.getDemoTransactionRef()) + "\""
        );
    }

    public static String mapToJson(Map<?, ?> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String listToJson(Collection<?> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append(toJsonValue(item));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String toJsonValue(Object val) {
        if (val == null) return "null";
        if (val instanceof User) return userToJson((User) val);
        if (val instanceof Item) return itemToJson((Item) val);
        if (val instanceof Booking) return bookingToJson((Booking) val);
        if (val instanceof Payment) return paymentToJson((Payment) val);
        if (val instanceof Map) return mapToJson((Map<?, ?>) val);
        if (val instanceof Collection) return listToJson((Collection<?>) val);
        if (val instanceof Number || val instanceof Boolean) return val.toString();
        return "\"" + escape(val.toString()) + "\"";
    }

    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        // Matches: "key" : "value" or "key" : 123 or "key" : true/false
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([^,\\}\\s]+))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String strVal = matcher.group(2);
            String rawVal = matcher.group(3);
            String value = strVal != null ? strVal : (rawVal != null ? rawVal.trim() : "");
            if ("null".equals(value)) value = "";
            map.put(key, value);
        }
        return map;
    }
}
