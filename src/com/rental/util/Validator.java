package com.rental.util;

import java.time.LocalDate;
import java.util.regex.Pattern;

public class Validator {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static boolean isPositive(double value) {
        return value > 0;
    }

    public static boolean isValidDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) return false;
        return !end.isBefore(start);
    }

    public static boolean isFutureOrToday(LocalDate date) {
        if (date == null) return false;
        return !date.isBefore(LocalDate.now());
    }
}
