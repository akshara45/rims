package com.rental.util;

import com.rental.user.User;
import com.rental.util.DBConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Base64;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class SecurityUtil {
    private static final String LEGACY_SALT = "RIMS_SECURE_SALT_2026";
    private static final String HASH_PREFIX = "PBKDF2$";
    private static final int PBKDF2_ITERATIONS = 180_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final long SESSION_TTL_SECONDS = 12 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) return "";
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derivePasswordHash(password, salt, PBKDF2_ITERATIONS);
        return HASH_PREFIX + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(String inputPassword, String storedHash) {
        if (inputPassword == null || storedHash == null) return false;
        if (storedHash.startsWith(HASH_PREFIX)) {
            try {
                String[] parts = storedHash.split("\\$", 4);
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                byte[] actual = derivePasswordHash(inputPassword, salt, iterations);
                return MessageDigest.isEqual(expected, actual);
            } catch (RuntimeException e) {
                return false;
            }
        }
        // Older local databases used a fixed-salt SHA-256 hash (or, in older revisions, plain text).
        return MessageDigest.isEqual(legacyHash(inputPassword).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)) || inputPassword.equals(storedHash);
    }

    public static boolean needsPasswordUpgrade(String storedHash) {
        return storedHash == null || !storedHash.startsWith(HASH_PREFIX);
    }

    private static byte[] derivePasswordHash(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 password hashing is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String legacyHash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(LEGACY_SALT.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    public static String createSession(User user) {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String sql = "INSERT INTO user_sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (PreparedStatement cleanup = conn.prepareStatement("DELETE FROM user_sessions WHERE expires_at<=CURRENT_TIMESTAMP")) {
                cleanup.executeUpdate();
            }
            stmt.setString(1, tokenHash(token));
            stmt.setString(2, user.getUserId());
            stmt.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(SESSION_TTL_SECONDS)));
            stmt.executeUpdate();
            return token;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist the login session", e);
        }
    }

    public static User getUserFromToken(String token) {
        if (token == null || token.isEmpty()) return null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        String sql = """
            SELECT u.user_id, u.name, u.email, u.role, u.phone, u.password, u.created_at
            FROM user_sessions s JOIN users u ON u.user_id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP
            """;
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenHash(token));
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new User(rs.getString("user_id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("role"), rs.getString("phone"), rs.getString("password"), rs.getString("created_at"));
            }
        } catch (SQLException e) {
            System.err.println("[SecurityUtil] Could not validate session: " + e.getMessage());
            return null;
        }
    }

    public static void invalidateSession(String token) {
        if (token == null) return;
        if (token.startsWith("Bearer ")) token = token.substring(7).trim();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM user_sessions WHERE token_hash=?")) {
            stmt.setString(1, tokenHash(token));
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SecurityUtil] Could not invalidate session: " + e.getMessage());
        }
    }

    private static String tokenHash(String token) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
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
