package com.rental.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

public class DBConnection {
    private static final String DB_URL = "jdbc:sqlite:rental_system.db";
    private static boolean initialized = false;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Warning: SQLite JDBC Driver not found in classpath. " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    public static synchronized void initializeDatabase() {
        if (initialized) return;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Users Table (Customer & Single Admin)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'CUSTOMER',
                    phone TEXT,
                    created_at TEXT NOT NULL
                );
            """);

            // 2. Items Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    item_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    description TEXT,
                    rental_price REAL NOT NULL,
                    available INTEGER NOT NULL DEFAULT 1,
                    image_url TEXT
                );
            """);

            // 3. Rentals Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rentals (
                    rental_id TEXT PRIMARY KEY,
                    customer_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    start_date TEXT NOT NULL,
                    end_date TEXT NOT NULL,
                    number_of_days INTEGER NOT NULL,
                    daily_rate REAL NOT NULL,
                    total_amount REAL NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    created_at TEXT NOT NULL,
                    returned_at TEXT,
                    FOREIGN KEY(customer_id) REFERENCES users(user_id) ON DELETE CASCADE,
                    FOREIGN KEY(item_id) REFERENCES items(item_id) ON DELETE CASCADE
                );
            """);

            // 4. Payments Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    payment_id TEXT PRIMARY KEY,
                    rental_id TEXT NOT NULL,
                    amount REAL NOT NULL,
                    method TEXT NOT NULL,
                    payment_date TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PAID',
                    FOREIGN KEY(rental_id) REFERENCES rentals(rental_id) ON DELETE CASCADE
                );
            """);

            // Seed initial data
            seedInitialData(conn);

            initialized = true;
            System.out.println("[DBConnection] SQLite database initialized successfully at " + DB_URL);
        } catch (SQLException e) {
            System.err.println("[DBConnection] Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void seedInitialData(Connection conn) throws SQLException {
        // Seed users if empty: exactly ONE admin and default customers
        try (Statement checkStmt = conn.createStatement();
             ResultSet rs = checkStmt.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertUser = "INSERT INTO users (user_id, name, email, password, role, phone, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertUser)) {
                    String today = LocalDate.now().toString();

                    // 1. SINGLE ADMIN ACCOUNT
                    pstmt.setString(1, "USR-ADMIN");
                    pstmt.setString(2, "System Administrator");
                    pstmt.setString(3, "admin@rental.com");
                    pstmt.setString(4, SecurityUtil.hashPassword("admin123"));
                    pstmt.setString(5, "ADMIN");
                    pstmt.setString(6, "+91 98765 43210");
                    pstmt.setString(7, today);
                    pstmt.executeUpdate();

                    // 2. Demo Customer 1
                    pstmt.setString(1, "USR-101");
                    pstmt.setString(2, "Alex Rivera");
                    pstmt.setString(3, "alex@example.com");
                    pstmt.setString(4, SecurityUtil.hashPassword("customer123"));
                    pstmt.setString(5, "CUSTOMER");
                    pstmt.setString(6, "+91 98765 11111");
                    pstmt.setString(7, today);
                    pstmt.executeUpdate();

                    // 3. Demo Customer 2
                    pstmt.setString(1, "USR-102");
                    pstmt.setString(2, "Priya Sharma");
                    pstmt.setString(3, "priya@example.com");
                    pstmt.setString(4, SecurityUtil.hashPassword("customer123"));
                    pstmt.setString(5, "CUSTOMER");
                    pstmt.setString(6, "+91 98765 22222");
                    pstmt.setString(7, today);
                    pstmt.executeUpdate();
                }
            }
        }

        // Seed items with exact Indian Rupee (INR) prices
        try (Statement checkStmt = conn.createStatement();
             ResultSet rs = checkStmt.executeQuery("SELECT COUNT(*) FROM items")) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertItem = "INSERT INTO items (item_id, name, category, description, rental_price, available, image_url) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertItem)) {
                    Object[][] seedItems = {
                        {"ITM-101", "Sony Alpha A7 IV Mirrorless Camera", "Photography", "Professional full-frame mirrorless camera suitable for high-resolution photography and 4K 60p video. 28-70mm lens included.", 4500.0, 1, "camera"},
                        {"ITM-102", "DJI Mavic 3 Pro Cine Drone", "Photography", "Triple-camera drone with Hasselblad 4/3 CMOS sensor, omnidirectional obstacle sensing, and 43-minute flight endurance.", 8000.0, 1, "drone"},
                        {"ITM-103", "Bose S1 Pro+ Wireless PA Speaker", "Audio & Sound", "All-in-one portable Bluetooth PA system with built-in 3-channel mixer, wireless mic support, and 11-hour battery life.", 3500.0, 1, "speaker"},
                        {"ITM-104", "Epson Home Cinema 4K PRO-UHD Projector", "Electronics", "High-brightness 3,000 lumens 4K HDR home theater projector with dual HDMI ports and ultra-sharp contrast.", 3000.0, 1, "projector"},
                        {"ITM-105", "PlayStation 5 Console + 2 DualSense Controllers", "Gaming", "Ultra-high-speed SSD gaming console with ray tracing, 4K HDR output, and 2 wireless DualSense haptic controllers.", 2500.0, 1, "gaming"},
                        {"ITM-106", "Coleman 6-Person Waterproof Tent", "Outdoor & Camping", "WeatherTec system weatherproof instant cabin tent with welded corners and rainfly. Setup in under 2 minutes.", 1800.0, 1, "tent"},
                        {"ITM-107", "Trek Marlin 7 Mountain Bike", "Sports & Mobility", "Trail-ready cross country hardtail mountain bike with RockShox fork, hydraulic disc brakes, and Shimano Deore 1x10 gears.", 1200.0, 1, "bike"},
                        {"ITM-108", "DeWalt 20V Max Cordless Drill Kit", "Tools", "Heavy-duty 20V brushless cordless hammer drill with two 4.0Ah lithium-ion batteries, high-speed charger, and contractor kit bag.", 900.0, 1, "tools"}
                    };

                    for (Object[] item : seedItems) {
                        pstmt.setString(1, (String) item[0]);
                        pstmt.setString(2, (String) item[1]);
                        pstmt.setString(3, (String) item[2]);
                        pstmt.setString(4, (String) item[3]);
                        pstmt.setDouble(5, (Double) item[4]);
                        pstmt.setInt(6, (Integer) item[5]);
                        pstmt.setString(7, (String) item[6]);
                        pstmt.executeUpdate();
                    }
                }
            }
        }
    }
}
