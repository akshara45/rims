package com.rental.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/** PostgreSQL connection/configuration and idempotent application schema setup. */
public final class DBConnection {
    private static final String DB_URL = secureJdbcUrl(requiredEnv("RIMS_DB_URL"));
    private static final String DB_USER = requiredEnv("RIMS_DB_USER");
    private static final String DB_PASSWORD = requiredEnv("RIMS_DB_PASSWORD");
    private static boolean schemaInitialized;

    private DBConnection() {}

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError("Required environment variable is not set: " + name);
        }
        return value.trim();
    }

    private static String secureJdbcUrl(String url) {
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new ExceptionInInitializerError("RIMS_DB_URL must be a PostgreSQL JDBC URL; local SQLite is not supported");
        }
        String authority = url.substring("jdbc:postgresql://".length()).split("[/?]", 2)[0].toLowerCase(java.util.Locale.ROOT);
        boolean loopback = authority.startsWith("localhost") || authority.startsWith("127.0.0.1") || authority.startsWith("[::1]");
        int queryStart = url.indexOf('?');
        if (queryStart >= 0) {
            for (String parameter : url.substring(queryStart + 1).split("&")) {
                if (parameter.toLowerCase(java.util.Locale.ROOT).startsWith("sslmode=")) {
                    String mode = parameter.substring(parameter.indexOf('=') + 1).toLowerCase(java.util.Locale.ROOT);
                    if (mode.equals("require") || mode.equals("verify-ca") || mode.equals("verify-full")) return url;
                    if (mode.equals("disable") && loopback) return url;
                    throw new ExceptionInInitializerError("RIMS_DB_URL must require TLS for remote PostgreSQL connections");
                }
            }
        }
        return url + (url.contains("?") ? "&" : "?") + "sslmode=require";
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /** Creates schema only. Also used by the one-time SQLite import tool before it copies data. */
    public static synchronized void initializeSchema() {
        if (schemaInitialized) return;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'CUSTOMER',
                    phone TEXT,
                    created_at VARCHAR(10) NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    item_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    rental_price DOUBLE PRECISION NOT NULL,
                    available INTEGER NOT NULL DEFAULT 1,
                    image_url TEXT,
                    rental_price_paise BIGINT NOT NULL DEFAULT 0
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rentals (
                    rental_id TEXT PRIMARY KEY,
                    customer_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                    item_id TEXT NOT NULL REFERENCES items(item_id) ON DELETE CASCADE,
                    start_date VARCHAR(10) NOT NULL,
                    end_date VARCHAR(10) NOT NULL,
                    number_of_days INTEGER NOT NULL,
                    daily_rate DOUBLE PRECISION NOT NULL,
                    total_amount DOUBLE PRECISION NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    created_at VARCHAR(10) NOT NULL,
                    returned_at VARCHAR(10),
                    payment_status TEXT NOT NULL DEFAULT 'PENDING',
                    payment_method TEXT NOT NULL DEFAULT 'CASH_ON_PICKUP',
                    late_fee DOUBLE PRECISION NOT NULL DEFAULT 0,
                    late_days INTEGER NOT NULL DEFAULT 0,
                    total_due DOUBLE PRECISION NOT NULL DEFAULT 0,
                    late_fee_status TEXT NOT NULL DEFAULT 'NOT_DUE',
                    daily_rate_paise BIGINT NOT NULL DEFAULT 0,
                    total_amount_paise BIGINT NOT NULL DEFAULT 0
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    payment_id TEXT PRIMARY KEY,
                    rental_id TEXT NOT NULL REFERENCES rentals(rental_id) ON DELETE CASCADE,
                    amount DOUBLE PRECISION NOT NULL,
                    method TEXT NOT NULL,
                    payment_date VARCHAR(10) NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PAID',
                    payment_type TEXT NOT NULL DEFAULT 'RENTAL',
                    demo_transaction_ref TEXT,
                    amount_paise BIGINT NOT NULL DEFAULT 0
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS returns (
                    return_id TEXT PRIMARY KEY,
                    rental_id TEXT NOT NULL UNIQUE REFERENCES rentals(rental_id) ON DELETE CASCADE,
                    actual_return_date VARCHAR(10) NOT NULL,
                    days_late INTEGER NOT NULL DEFAULT 0,
                    processed_by TEXT REFERENCES users(user_id),
                    condition_notes TEXT,
                    created_at VARCHAR(10) NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS late_fees (
                    late_fee_id TEXT PRIMARY KEY,
                    rental_id TEXT NOT NULL UNIQUE REFERENCES rentals(rental_id) ON DELETE CASCADE,
                    days_late INTEGER NOT NULL DEFAULT 0,
                    rate_per_day_paise BIGINT NOT NULL DEFAULT 0,
                    amount_paise BIGINT NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'NOT_DUE',
                    created_at VARCHAR(10) NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_sessions (
                    token_hash TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                    expires_at TIMESTAMPTZ NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rentals_item_dates ON rentals(item_id, start_date, end_date, status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rentals_customer ON rentals(customer_id, created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_rental ON payments(rental_id, payment_type, payment_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_expiry ON user_sessions(user_id, expires_at)");
            stmt.execute("INSERT INTO schema_migrations(version) VALUES (1) ON CONFLICT (version) DO NOTHING");
            conn.commit();
            schemaInitialized = true;
            System.out.println("[DBConnection] PostgreSQL schema initialized successfully.");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not safely initialize the PostgreSQL schema", e);
        }
    }

    public static synchronized void initializeDatabase() {
        initializeSchema();
        try (Connection conn = getConnection()) {
            seedInitialData(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not seed initial RIMS data", e);
        }
    }

    private static void seedInitialData(Connection conn) throws SQLException {
        if (tableIsEmpty(conn, "users")) {
            String adminEmail = System.getenv("RIMS_SEED_ADMIN_EMAIL");
            String adminPassword = System.getenv("RIMS_SEED_ADMIN_PASSWORD");
            if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.length() < 12) {
                throw new IllegalStateException("An empty database requires RIMS_SEED_ADMIN_EMAIL and RIMS_SEED_ADMIN_PASSWORD (at least 12 characters)");
            }
            try (PreparedStatement insert = conn.prepareStatement("""
                    INSERT INTO users (user_id,name,email,password,role,phone,created_at)
                    VALUES (?,?,?,?,?,?,?)
                    """)) {
                insert.setString(1, "USR-ADMIN");
                insert.setString(2, "System Administrator");
                insert.setString(3, adminEmail.trim().toLowerCase());
                insert.setString(4, SecurityUtil.hashPassword(adminPassword));
                insert.setString(5, "ADMIN");
                insert.setString(6, "");
                insert.setString(7, LocalDate.now().toString());
                insert.executeUpdate();
            }
            seedDemoCustomer(conn, "USR-101", "Alex Rivera", "alex@example.com", "+91 98765 11111");
            seedDemoCustomer(conn, "USR-102", "Priya Sharma", "priya@example.com", "+91 98765 22222");
        }

        if (tableIsEmpty(conn, "items")) {
            Object[][] items = {
                {"ITM-101", "Sony Alpha A7 IV Mirrorless Camera", "Photography", "Professional full-frame mirrorless camera suitable for high-resolution photography and 4K 60p video. 28-70mm lens included.", 4500.0, "camera"},
                {"ITM-102", "DJI Mavic 3 Pro Cine Drone", "Photography", "Triple-camera drone with Hasselblad 4/3 CMOS sensor, omnidirectional obstacle sensing, and 43-minute flight endurance.", 8000.0, "drone"},
                {"ITM-103", "Bose S1 Pro+ Wireless PA Speaker", "Audio & Sound", "All-in-one portable Bluetooth PA system with built-in 3-channel mixer, wireless mic support, and 11-hour battery life.", 3500.0, "speaker"},
                {"ITM-104", "Epson Home Cinema 4K PRO-UHD Projector", "Electronics", "High-brightness 3,000 lumens 4K HDR home theater projector with dual HDMI ports and ultra-sharp contrast.", 3000.0, "projector"},
                {"ITM-105", "PlayStation 5 Console + 2 DualSense Controllers", "Gaming", "Ultra-high-speed SSD gaming console with ray tracing, 4K HDR output, and 2 wireless DualSense haptic controllers.", 2500.0, "gaming"},
                {"ITM-106", "Coleman 6-Person Waterproof Tent", "Outdoor & Camping", "WeatherTec system weatherproof instant cabin tent with welded corners and rainfly. Setup in under 2 minutes.", 1800.0, "tent"},
                {"ITM-107", "Trek Marlin 7 Mountain Bike", "Sports & Mobility", "Trail-ready cross country hardtail mountain bike with RockShox fork, hydraulic disc brakes, and Shimano Deore 1x10 gears.", 1200.0, "bike"},
                {"ITM-108", "DeWalt 20V Max Cordless Drill Kit", "Tools", "Heavy-duty 20V brushless cordless hammer drill with batteries, charger, and contractor kit bag.", 900.0, "tools"}
            };
            try (PreparedStatement insert = conn.prepareStatement("""
                    INSERT INTO items (item_id,name,category,description,rental_price,available,image_url,rental_price_paise)
                    VALUES (?,?,?,?,?,1,?,?)
                    """)) {
                for (Object[] item : items) {
                    insert.setString(1, (String) item[0]);
                    insert.setString(2, (String) item[1]);
                    insert.setString(3, (String) item[2]);
                    insert.setString(4, (String) item[3]);
                    double price = (Double) item[4];
                    insert.setDouble(5, price);
                    insert.setString(6, (String) item[5]);
                    insert.setLong(7, Math.round(price * 100));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
    }

    private static void seedDemoCustomer(Connection conn, String id, String name, String email, String phone) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement("""
                INSERT INTO users (user_id,name,email,password,role,phone,created_at) VALUES (?,?,?,?,?,?,?)
                """)) {
            insert.setString(1, id);
            insert.setString(2, name);
            insert.setString(3, email);
            insert.setString(4, SecurityUtil.hashPassword("customer123"));
            insert.setString(5, "CUSTOMER");
            insert.setString(6, phone);
            insert.setString(7, LocalDate.now().toString());
            insert.executeUpdate();
        }
    }

    private static boolean tableIsEmpty(Connection conn, String table) throws SQLException {
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid table name");
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() && rs.getLong(1) == 0;
        }
    }
}
