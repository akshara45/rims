package com.rental.web;

import com.rental.booking.Booking;
import com.rental.booking.BookingDAO;
import com.rental.item.Item;
import com.rental.item.ItemDAO;
import com.rental.payment.Payment;
import com.rental.payment.PaymentDAO;
import com.rental.report.ReportGenerator;
import com.rental.user.User;
import com.rental.user.UserDAO;
import com.rental.util.DBConnection;
import com.rental.util.JsonUtil;
import com.rental.util.SecurityUtil;
import com.rental.util.Validator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class RentalHttpServer {
    private final int port;
    private HttpServer server;
    private final UserDAO userDAO = new UserDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ReportGenerator reportGenerator = new ReportGenerator();

    public RentalHttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        DBConnection.initializeDatabase();

        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Authentication & Profile
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/auth/register", this::handleRegister);
        server.createContext("/api/auth/me", this::handleMe);
        server.createContext("/api/auth/logout", this::handleLogout);
        server.createContext("/api/profile", this::handleProfile);
        server.createContext("/api/health", this::handleHealth);

        // Items (Public browse / Admin CRUD)
        server.createContext("/api/items", this::handleItems);

        // Rentals (Customer booking & return / Admin management)
        server.createContext("/api/rentals/my", this::handleMyRentals);
        server.createContext("/api/rentals/status", this::handleRentalStatus);
        server.createContext("/api/rentals", this::handleRentals);
        server.createContext("/api/payments/simulate", this::handleSimulateLateFeePayment);

        // Admin-only: Customers & Analytics
        server.createContext("/api/customers", this::handleCustomers);
        server.createContext("/api/admin/analytics", this::handleAdminAnalytics);
        server.createContext("/api/reports", this::handleAdminAnalytics); // backward compatibility

        // Static files (serves from ./web)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("==========================================================");
        System.out.println("   RIMS (Rental Item Management System) is LIVE");
        System.out.println("   HTTP port: " + port);
        System.out.println("==========================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // -----------------------------------------------------------------
    // AUTHENTICATION & PROFILE HANDLERS
    // -----------------------------------------------------------------

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = readBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);
        String email = data.get("email");
        String password = data.get("password");

        if (!Validator.isNotEmpty(email) || !Validator.isNotEmpty(password)) {
            sendResponse(exchange, 400, "{\"error\":\"Email and password are required\"}");
            return;
        }

        User user = userDAO.authenticate(email, password);
        if (user != null) {
            String token = SecurityUtil.createSession(user);
            String resp = "{\"success\":true,\"token\":\"" + token + "\",\"user\":" + JsonUtil.userToJson(user) + "}";
            sendResponse(exchange, 200, resp);
        } else {
            sendResponse(exchange, 401, "{\"error\":\"Invalid email or password\"}");
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = readBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);
        String name = data.get("name");
        String email = data.get("email");
        String password = data.get("password");
        String phone = data.get("phone");

        if (!Validator.isNotEmpty(name) || !Validator.isValidEmail(email) || !Validator.isNotEmpty(password)) {
            sendResponse(exchange, 400, "{\"error\":\"Please provide a valid full name, email, and password\"}");
            return;
        }

        if (userDAO.getUserByEmail(email) != null) {
            sendResponse(exchange, 409, "{\"error\":\"An account with this email address already exists\"}");
            return;
        }

        String customerId = "CUST-" + UUID.randomUUID();
        User customer = new User(customerId, name, email, "CUSTOMER", phone != null ? phone : "", "", LocalDate.now().toString());

        boolean ok = userDAO.registerCustomer(customer, password);
        if (ok) {
            String token = SecurityUtil.createSession(customer);
            String resp = "{\"success\":true,\"token\":\"" + token + "\",\"user\":" + JsonUtil.userToJson(customer) + "}";
            sendResponse(exchange, 201, resp);
        } else {
            sendResponse(exchange, 500, "{\"error\":\"Failed to register customer account\"}");
        }
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendResponse(exchange, 401, "{\"error\":\"Session expired or invalid. Please sign in.\"}");
            return;
        }
        sendResponse(exchange, 200, "{\"user\":" + JsonUtil.userToJson(user) + "}");
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        String token = getAuthToken(exchange);
        if (token != null) {
            SecurityUtil.invalidateSession(token);
        }
        sendResponse(exchange, 200, "{\"success\":true}");
    }

    private void handleProfile(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();
        if ("GET".equals(method)) {
            List<Booking> rentals = bookingDAO.getBookingsByUser(user.getUserId());
            long active = rentals.stream().filter(r -> "CONFIRMED".equalsIgnoreCase(r.getStatus())
                    || "ACTIVE".equalsIgnoreCase(r.getStatus()) || "APPROVED".equalsIgnoreCase(r.getStatus())).count();
            double spent = rentals.stream()
                .mapToDouble(r -> ("PAID".equalsIgnoreCase(r.getPaymentStatus()) ? r.getTotalAmount() : 0.0)
                        + ("PAID".equalsIgnoreCase(r.getLateFeeStatus()) ? r.getLateFee() : 0.0))
                .sum();

            Map<String, Object> profileData = new HashMap<>();
            profileData.put("user", user);
            profileData.put("totalRentals", rentals.size());
            profileData.put("activeRentals", active);
            profileData.put("totalSpent", spent);

            sendResponse(exchange, 200, JsonUtil.mapToJson(profileData));
        } else if ("PUT".equals(method)) {
            String body = readBody(exchange);
            Map<String, String> data = JsonUtil.parseSimpleJson(body);
            String name = data.get("name");
            String phone = data.get("phone");

            if (name != null && !name.trim().isEmpty()) user.setName(name.trim());
            if (phone != null) user.setPhone(phone.trim());

            boolean ok = userDAO.updateProfile(user.getUserId(), user.getName(), user.getPhone());
            if (ok) {
                sendResponse(exchange, 200, "{\"success\":true,\"user\":" + JsonUtil.userToJson(user) + "}");
            } else {
                sendResponse(exchange, 500, "{\"error\":\"Failed to update profile\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    // -----------------------------------------------------------------
    // ITEMS HANDLER (Public search / Admin CRUD)
    // -----------------------------------------------------------------

    private void handleItems(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        String method = exchange.getRequestMethod().toUpperCase();

        switch (method) {
            case "GET" -> {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String query = params.get("search");
                String category = params.get("category");
                String id = params.get("id");
                User requestingUser = getAuthenticatedUser(exchange);
                boolean adminView = requestingUser != null && requestingUser.isAdmin();
                LocalDate rangeStart = null;
                LocalDate rangeEnd = null;
                try {
                    if (params.containsKey("startDate") || params.containsKey("endDate")) {
                        rangeStart = LocalDate.parse(params.get("startDate"));
                        rangeEnd = LocalDate.parse(params.get("endDate"));
                        if (!Validator.isValidDateRange(rangeStart, rangeEnd)) throw new IllegalArgumentException("Invalid date range");
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\":\"Provide valid startDate and endDate values (YYYY-MM-DD)\"}");
                    return;
                }

                if (id != null && !id.isEmpty()) {
                    Item item = itemDAO.getItemById(id);
                    if (item != null) {
                        if (!adminView) {
                            LocalDate checkStart = rangeStart != null ? rangeStart : LocalDate.now();
                            LocalDate checkEnd = rangeEnd != null ? rangeEnd : LocalDate.now();
                            item.setAvailable(itemDAO.isAvailableForRental(id, checkStart, checkEnd));
                        }
                        sendResponse(exchange, 200, JsonUtil.itemToJson(item));
                    } else {
                        sendResponse(exchange, 404, "{\"error\":\"Item not found\"}");
                    }
                    return;
                }

                List<Item> items = adminView && (query == null || query.isBlank())
                        && (category == null || category.isBlank()) && rangeStart == null
                        ? itemDAO.getAllItems()
                        : itemDAO.searchItems(query, category, rangeStart, rangeEnd);
                sendResponse(exchange, 200, JsonUtil.listToJson(items));
            }
            case "POST" -> {
                // Strict Admin Authorization
                User admin = getAuthenticatedUser(exchange);
                if (admin == null || !admin.isAdmin()) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only the Administrator can add inventory items\"}");
                    return;
                }

                String body = readBody(exchange);
                Map<String, String> data = JsonUtil.parseSimpleJson(body);

                String name = data.get("name");
                String category = data.get("category");
                String description = data.get("description");
                String priceStr = data.get("rentalPrice");
                String imageUrl = data.get("imageUrl");

                if (!Validator.isNotEmpty(name) || !Validator.isNotEmpty(priceStr)) {
                    sendResponse(exchange, 400, "{\"error\":\"Item name and rental price (₹) are required\"}");
                    return;
                }

                double price = Double.parseDouble(priceStr);
                String itemId = "ITM-" + (System.currentTimeMillis() % 1000000);
                Item item = new Item(itemId, name, category, description, price, true, imageUrl);

                boolean ok = itemDAO.addItem(item);
                if (ok) {
                    sendResponse(exchange, 201, "{\"success\":true,\"item\":" + JsonUtil.itemToJson(item) + "}");
                } else {
                    sendResponse(exchange, 500, "{\"error\":\"Database failure while adding item\"}");
                }
            }
            case "PUT" -> {
                // Strict Admin Authorization
                User admin = getAuthenticatedUser(exchange);
                if (admin == null || !admin.isAdmin()) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only the Administrator can modify inventory items\"}");
                    return;
                }

                String body = readBody(exchange);
                Map<String, String> data = JsonUtil.parseSimpleJson(body);
                String itemId = data.get("itemId");

                Item item = itemDAO.getItemById(itemId);
                if (item == null) {
                    sendResponse(exchange, 404, "{\"error\":\"Item not found\"}");
                    return;
                }

                if (data.containsKey("name") && Validator.isNotEmpty(data.get("name"))) item.setName(data.get("name"));
                if (data.containsKey("category") && Validator.isNotEmpty(data.get("category"))) item.setCategory(data.get("category"));
                if (data.containsKey("description")) item.setDescription(data.get("description"));
                if (data.containsKey("rentalPrice") && !data.get("rentalPrice").isEmpty()) item.setRentalPrice(Double.parseDouble(data.get("rentalPrice")));
                if (data.containsKey("available") && !data.get("available").isEmpty()) item.setAvailable(Boolean.parseBoolean(data.get("available")));
                if (data.containsKey("imageUrl")) item.setImageUrl(data.get("imageUrl"));

                boolean ok = itemDAO.updateItem(item);
                if (ok) {
                    sendResponse(exchange, 200, "{\"success\":true,\"item\":" + JsonUtil.itemToJson(item) + "}");
                } else {
                    sendResponse(exchange, 500, "{\"error\":\"Failed to update item\"}");
                }
            }
            case "DELETE" -> {
                // Strict Admin Authorization
                User admin = getAuthenticatedUser(exchange);
                if (admin == null || !admin.isAdmin()) {
                    sendResponse(exchange, 403, "{\"error\":\"Forbidden: Only the Administrator can delete inventory items\"}");
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String itemId = params.get("id");
                if (!Validator.isNotEmpty(itemId)) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing item id parameter\"}");
                    return;
                }

                boolean ok = itemDAO.deleteItem(itemId);
                if (ok) {
                    sendResponse(exchange, 200, "{\"success\":true}");
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Cannot delete item with active or pending rentals\"}");
                }
            }
            default -> sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    // -----------------------------------------------------------------
    // RENTALS HANDLERS
    // -----------------------------------------------------------------

    private void handleRentals(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        String method = exchange.getRequestMethod().toUpperCase();

        if ("GET".equals(method)) {
            // Admin only access to all rentals
            User admin = getAuthenticatedUser(exchange);
            if (admin == null || !admin.isAdmin()) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden: Admin access required to view all rentals\"}");
                return;
            }
            List<Booking> rentals = bookingDAO.getAllBookings();
            sendResponse(exchange, 200, JsonUtil.listToJson(rentals));
        } else if ("POST".equals(method)) {
            // Customer rental creation
            User customer = getAuthenticatedUser(exchange);
            if (customer == null) {
                sendResponse(exchange, 401, "{\"error\":\"Please sign in to rent an item\"}");
                return;
            }

            String body = readBody(exchange);
            Map<String, String> data = JsonUtil.parseSimpleJson(body);

            String itemId = data.get("itemId");
            String startStr = data.get("startDate");
            String endStr = data.get("endDate");
            String paymentMethod = data.getOrDefault("paymentMethod", "CREDIT_CARD").toUpperCase(Locale.ROOT);
            if ("CASH".equals(paymentMethod)) paymentMethod = "CASH_ON_PICKUP";
            if (!Set.of("UPI", "NET_BANKING", "CREDIT_CARD", "DEBIT_CARD", "CASH_ON_PICKUP").contains(paymentMethod)) {
                sendResponse(exchange, 400, "{\"error\":\"Choose a supported simulated payment method\"}");
                return;
            }

            if (!Validator.isNotEmpty(itemId) || !Validator.isNotEmpty(startStr) || !Validator.isNotEmpty(endStr)) {
                sendResponse(exchange, 400, "{\"error\":\"Please select rental start and end dates\"}");
                return;
            }

            Item item = itemDAO.getItemById(itemId);
            if (item == null) {
                sendResponse(exchange, 404, "{\"error\":\"Item not found\"}");
                return;
            }
            LocalDate start;
            LocalDate end;
            try {
                start = LocalDate.parse(startStr);
                end = LocalDate.parse(endStr);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"Rental dates must use YYYY-MM-DD format\"}");
                return;
            }
            if (!Validator.isValidDateRange(start, end)) {
                sendResponse(exchange, 400, "{\"error\":\"Rental end date must be on or after start date\"}");
                return;
            }
            if (!Validator.isFutureOrToday(start)) {
                sendResponse(exchange, 400, "{\"error\":\"Rental start date cannot be in the past\"}");
                return;
            }
            if (!itemDAO.isAvailableForRental(itemId, start, end)) {
                sendResponse(exchange, 400, "{\"error\":\"This item is unavailable for the selected dates\"}");
                return;
            }

            int days = Booking.calculateDays(start, end);
            double dailyRate = item.getRentalPrice();
            double totalAmount = days * dailyRate;

            String rentalId = "RNT-" + UUID.randomUUID();
            boolean isCash = "CASH_ON_PICKUP".equals(paymentMethod);
            String outcome = data.getOrDefault("paymentOutcome", "SUCCESS").toUpperCase(Locale.ROOT);
            if (!isCash && !Set.of("SUCCESS", "FAILED").contains(outcome)) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid simulated payment outcome\"}");
                return;
            }
            boolean paymentFailed = !isCash && "FAILED".equals(outcome);
            String bookingStatus = paymentFailed ? "CANCELLED" : "CONFIRMED";
            String paymentStatus = isCash ? "PENDING" : (paymentFailed ? "FAILED" : "PAID");
            String demoRef = "PAID".equals(paymentStatus)
                    ? "DEMO-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT)
                    : null;

            Booking rental = new Booking(rentalId, customer, item, start, end, days, dailyRate, totalAmount, bookingStatus, LocalDate.now(), null);
            rental.setPaymentStatus(paymentStatus);
            rental.setPaymentMethod(paymentMethod);

            Payment payment = new Payment("PAY-" + UUID.randomUUID(), rental, totalAmount, paymentMethod,
                    LocalDate.now(), paymentStatus, "RENTAL", demoRef);
            boolean success = bookingDAO.createBooking(rental, payment);
            if (!success) {
                sendResponse(exchange, 400, "{\"error\":\"This item was just booked by another user or is currently unavailable.\"}");
                return;
            }

            String resp = "{\"success\":" + !paymentFailed + ",\"paymentFailed\":" + paymentFailed
                    + ",\"rental\":" + JsonUtil.bookingToJson(rental) + ",\"payment\":" + JsonUtil.paymentToJson(payment) + "}";
            sendResponse(exchange, 201, resp);
        } else {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void handleMyRentals(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        User customer = getAuthenticatedUser(exchange);
        if (customer == null) {
            sendResponse(exchange, 401, "{\"error\":\"Please sign in to view your rentals\"}");
            return;
        }

        List<Booking> rentals = bookingDAO.getBookingsByUser(customer.getUserId());
        sendResponse(exchange, 200, JsonUtil.listToJson(rentals));
    }

    private void handleRentalStatus(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod()) && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }

        String body = readBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);
        String rentalId = data.get("rentalId");
        String newStatus = data.get("status");
        String returnDateInput = data.get("actualReturnDate");

        if (!Validator.isNotEmpty(rentalId) || !Validator.isNotEmpty(newStatus)) {
            sendResponse(exchange, 400, "{\"error\":\"Rental ID and status are required\"}");
            return;
        }

        Booking rental = bookingDAO.getBookingById(rentalId);
        if (rental == null) {
            sendResponse(exchange, 404, "{\"error\":\"Rental record not found\"}");
            return;
        }

        // Customer can only return or cancel their own rental
        if (!user.isAdmin()) {
            if (!rental.getUser().getUserId().equals(user.getUserId())) {
                sendResponse(exchange, 403, "{\"error\":\"Forbidden: You cannot modify other customers' rentals\"}");
                return;
            }
            if (!"RETURNED".equalsIgnoreCase(newStatus) && !"CANCELLED".equalsIgnoreCase(newStatus)) {
                sendResponse(exchange, 403, "{\"error\":\"Customers can only mark rentals as RETURNED or CANCELLED\"}");
                return;
            }
            if (returnDateInput != null && !returnDateInput.isEmpty()) {
                sendResponse(exchange, 403, "{\"error\":\"Only an administrator can enter a recorded return date\"}");
                return;
            }
        }

        LocalDate returnDate = LocalDate.now();
        if ("RETURNED".equalsIgnoreCase(newStatus) && returnDateInput != null && !returnDateInput.isEmpty()) {
            try {
                returnDate = LocalDate.parse(returnDateInput);
                if (returnDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("Future return date");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"Return date must be a valid date no later than today\"}");
                return;
            }
        }

        boolean ok = "RETURNED".equalsIgnoreCase(newStatus)
                ? bookingDAO.processReturn(rentalId, user.getUserId(), returnDate)
                : bookingDAO.updateStatus(rentalId, newStatus);
        if (ok) {
            Booking updated = bookingDAO.getBookingById(rentalId);
            sendResponse(exchange, 200, "{\"success\":true,\"rental\":" + JsonUtil.bookingToJson(updated) + "}");
        } else {
            sendResponse(exchange, 500, "{\"error\":\"Failed to update rental status\"}");
        }
    }

    private void handleSimulateLateFeePayment(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        User user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendResponse(exchange, 401, "{\"error\":\"Please sign in to pay this late fee\"}");
            return;
        }
        Map<String, String> data = JsonUtil.parseSimpleJson(readBody(exchange));
        String rentalId = data.get("rentalId");
        String method = data.getOrDefault("paymentMethod", "UPI").toUpperCase(Locale.ROOT);
        if (!Validator.isNotEmpty(rentalId) || !Set.of("UPI", "NET_BANKING", "CREDIT_CARD", "DEBIT_CARD").contains(method)) {
            sendResponse(exchange, 400, "{\"error\":\"A rental ID and simulated payment method are required\"}");
            return;
        }
        Booking rental = bookingDAO.getBookingById(rentalId);
        if (rental == null) {
            sendResponse(exchange, 404, "{\"error\":\"Rental record not found\"}");
            return;
        }
        if (!user.isAdmin() && (rental.getUser() == null || !rental.getUser().getUserId().equals(user.getUserId()))) {
            sendResponse(exchange, 403, "{\"error\":\"You cannot pay another customer's late fee\"}");
            return;
        }
        if (!"PENDING".equalsIgnoreCase(rental.getLateFeeStatus())) {
            sendResponse(exchange, 400, "{\"error\":\"There is no pending late fee for this rental\"}");
            return;
        }
        if (!paymentDAO.settleLateFee(rentalId, method)) {
            sendResponse(exchange, 409, "{\"error\":\"Late-fee payment could not be completed\"}");
            return;
        }
        Payment payment = paymentDAO.getLateFeePayment(rentalId);
        Booking updated = bookingDAO.getBookingById(rentalId);
        sendResponse(exchange, 200, "{\"success\":true,\"rental\":" + JsonUtil.bookingToJson(updated)
                + ",\"payment\":" + JsonUtil.paymentToJson(payment) + "}");
    }

    // -----------------------------------------------------------------
    // ADMIN-ONLY HANDLERS (CUSTOMERS & ANALYTICS)
    // -----------------------------------------------------------------

    private void handleCustomers(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        User admin = getAuthenticatedUser(exchange);
        if (admin == null || !admin.isAdmin()) {
            sendResponse(exchange, 403, "{\"error\":\"Forbidden: Admin access required\"}");
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        List<Map<String, Object>> customers = userDAO.getAllCustomersWithStats();
        sendResponse(exchange, 200, JsonUtil.listToJson(customers));
    }

    private void handleAdminAnalytics(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        User admin = getAuthenticatedUser(exchange);
        if (admin == null || !admin.isAdmin()) {
            sendResponse(exchange, 403, "{\"error\":\"Forbidden: Analytics are restricted to the Administrator\"}");
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        Map<String, Object> analytics = reportGenerator.getAdminAnalytics();
        sendResponse(exchange, 200, JsonUtil.mapToJson(analytics));
    }

    // -----------------------------------------------------------------
    // HELPER METHODS
    // -----------------------------------------------------------------

    private User getAuthenticatedUser(HttpExchange exchange) {
        String token = getAuthToken(exchange);
        return SecurityUtil.getUserFromToken(token);
    }

    private String getAuthToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            return authHeader;
        }
        Map<String, String> q = parseQueryParams(exchange.getRequestURI().getRawQuery());
        return q.get("token");
    }

    private boolean handleCors(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) return;
        try (java.sql.Connection connection = DBConnection.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery("SELECT 1")) {
            if (result.next()) {
                sendResponse(exchange, 200, "{\"status\":\"ok\",\"database\":\"connected\"}");
                return;
            }
        } catch (java.sql.SQLException e) {
            sendResponse(exchange, 503, "{\"status\":\"unavailable\",\"database\":\"disconnected\"}");
            return;
        }
        sendResponse(exchange, 503, "{\"status\":\"unavailable\",\"database\":\"disconnected\"}");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 0) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String val = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                params.put(key, val);
            }
        }
        return params;
    }

    // -----------------------------------------------------------------
    // STATIC FILE HANDLER
    // -----------------------------------------------------------------

    static class StaticFileHandler implements HttpHandler {
        private final File webRoot = new File("web");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File(webRoot, path.substring(1));
            if (!file.exists() || file.isDirectory()) {
                file = new File(webRoot, "index.html");
            }

            if (!file.exists()) {
                String notFound = "<h1>404 Not Found</h1><p>Frontend static files not found in web/.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String mime = getMimeType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, file.length());

            try (InputStream is = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }

        private String getMimeType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
            if (lower.endsWith(".css")) return "text/css; charset=utf-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.endsWith(".json")) return "application/json; charset=utf-8";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
