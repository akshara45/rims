package com.rental.util;

import com.rental.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SecurityUtil {
    private static final String SALT = "RIMS_SECURE_SALT_2026";
    private static final Map<String, User> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyPassword(String inputPassword, String storedHash) {
        if (inputPassword == null || storedHash == null) return false;
        String hashedInput = hashPassword(inputPassword);
        return hashedInput.equalsIgnoreCase(storedHash) || inputPassword.equals(storedHash);
    }

    public static String createSession(User user) {
        // Invalidate any previous session for the same user
        ACTIVE_SESSIONS.entrySet().removeIf(entry -> entry.getValue().getUserId().equals(user.getUserId()));
        String token = "RIMS-" + UUID.randomUUID().toString();
        ACTIVE_SESSIONS.put(token, user);
        return token;
    }

    public static User getUserFromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        return ACTIVE_SESSIONS.get(token);
    }

    public static void invalidateSession(String token) {
        if (token == null) return;
        if (token.startsWith("Bearer ")) token = token.substring(7).trim();
        ACTIVE_SESSIONS.remove(token);
    }

    public static boolean isAdmin(String token) {
        User user = getUserFromToken(token);
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    public static boolean isCustomer(String token) {
        User user = getUserFromToken(token);
        return user != null && "CUSTOMER".equalsIgnoreCase(user.getRole());
    }
}
