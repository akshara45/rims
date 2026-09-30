# Item Rental Management System (RIMS)

A professional, full-stack Java Item Rental Management System designed with strict role-based access control, persistent SQLite database storage, dynamic rental duration & pricing calculations in Indian Rupees (₹), and completely separated Customer and Administrator interfaces.

---

## 🏛️ System Architecture & User Roles

The system enforces **exactly two roles**:
1. **CUSTOMER**: Can register, browse inventory, rent items, calculate costs, track rental orders in "My Rentals", and manage profile details.
2. **ADMIN**: Exactly ONE predefined administrator account (`admin@rental.com`). Admin registration is strictly prohibited. Admin has access to the Executive Operations Dashboard, Inventory CRUD, Customer Directory, Rental Approvals & Return Processing, and Financial Analytics.

```
                    LOGIN (/api/auth/login)
                               |
                      Backend verifies user
                               |
                 +-------------+-------------+
                 |                           |
             CUSTOMER                      ADMIN
                 |                           |
                 v                           v
         Customer Interface           Admin Interface
         - Explore Items              - Operations Dashboard
         - Rent Now Modal             - Manage Inventory Items
         - My Rentals                 - Manage Customers
         - Customer Profile           - Manage Rentals & Returns
                                      - Analytics & Reports
```

---

## 🎨 Theme & Visual Design

- **Primary Theme**: Clean Corporate SaaS Design (White Background + Professional Navy Blue)
- **Primary Navy**: `#0F2747` | **Secondary Navy**: `#173B63` | **Accent Blue**: `#2563EB`
- **Background**: `#F8FAFC` (Page) / `#FFFFFF` (Surface Cards & Modals)
- **Currency**: **Indian Rupees (₹)** displayed everywhere across catalog, pricing calculators, receipts, and analytics.

---

## 🛠️ Technology Stack

- **Backend**: Java 21+ / Java 25 (Built-in `com.sun.net.httpserver.HttpServer` with Virtual Threads)
- **Database**: SQLite with JDBC (`rental_system.db`)
- **Security**: SHA-256 password hashing with salt, token-based session verification, role-based API authorization
- **Frontend**: Semantic HTML5, Vanilla CSS3 (Professional White + Navy Corporate Theme, Inter typography), Vanilla JavaScript ES6+
- **Architecture**: DAO Pattern (Data Access Objects) with RESTful API endpoints

---

## 📁 Project Structure

```
Rental-App-Management-System/
├── src/
│   ├── Main.java                          # Server bootstrap & initialization
│   └── com/
│       └── rental/
│           ├── user/
│           │   ├── User.java              # User domain model
│           │   └── UserDAO.java           # Customer registration & DB operations
│           ├── item/
│           │   ├── Item.java              # Item domain model
│           │   └── ItemDAO.java           # Inventory CRUD & availability ops
│           ├── booking/
│           │   ├── Booking.java           # Rental domain model & cost calculations
│           │   └── BookingDAO.java        # Transactional booking & return handling
│           ├── payment/
│           │   ├── Payment.java           # Payment ledger model
│           │   └── PaymentDAO.java        # Revenue calculation & transactions
│           ├── report/
│           │   └── ReportGenerator.java   # Real DB metrics & category breakdown
│           ├── util/
│           │   ├── DBConnection.java      # SQLite connection & seed initialization
│           │   ├── SecurityUtil.java      # SHA-256 password hashing & session tokens
│           │   ├── JsonUtil.java          # High-performance JSON serializer/parser
│           │   └── Validator.java         # Data validation helpers
│           └── web/
│               └── RentalHttpServer.java  # Embedded HTTP server & REST controllers
│
├── web/
│   ├── index.html                         # Unified Single-Page Application
│   ├── style.css                          # White + Navy Corporate SaaS Theme
│   └── app.js                             # Client-side state, auth, & API client
│
├── lib/                                   # SQLite JDBC & SLF4J native drivers
├── run.bat                                # 1-Click build & launch script for Windows
├── test_suite.ps1                         # Automated End-to-End Test Suite (32 tests)
├── .gitignore
└── README.md
```

---

## 🧪 Automated Testing

To run the complete automated test suite validating all 32 functional, security, and financial features:
```powershell
powershell -ExecutionPolicy Bypass -File "test_suite.ps1"
```

---

## 🚦 How to Run

### Method 1: Using `run.bat` (Recommended)
Double-click `run.bat` or run in terminal:
```cmd
run.bat
```

### Method 2: Manual Terminal Commands
1. **Compile**:
```powershell
javac -cp "lib/*" -d bin src/com/rental/*/*.java src/Main.java
```

2. **Run**:
```powershell
java -cp "bin;lib/*" Main
```

3. **Open in Browser**:
Visit: **[http://localhost:8080](http://localhost:8080)**

---

## 👥 Default Accounts

| Role | Name | Email | Password | Access Level |
|---|---|---|---|---|
| **Admin** | System Administrator | `admin@rental.com` | `admin123` | Full Dashboard & Inventory Management |
| **Customer** | Alex Rivera | `alex@example.com` | `customer123` | Equipment Catalog & Personal Rentals |
| **Customer** | Priya Sharma | `priya@example.com` | `customer123` | Equipment Catalog & Personal Rentals |
| **New Customers** | Any user | *Register via UI* | *Self-chosen* | Customer Portal |
