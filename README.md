# Rental-App-Management-System
A Java-based application for managing rental inventory, bookings, and customers.

# Folder Structure 
Rental-App-Management-System/
├── src/
│   └── com/
│       └── rental/
│           ├── user/
│           │   ├── User.java          # User model
│           │   └── UserDAO.java       # Database operations for users
│           │
│           ├── item/
│           │   ├── Item.java          # Item model
│           │   └── ItemDAO.java       # Database operations for items
│           │
│           ├── booking/
│           │   ├── Booking.java       # Booking model
│           │   └── BookingDAO.java    # Database operations for bookings
│           │
│           ├── payment/
│           │   ├── Payment.java       # Payment model
│           │   └── PaymentDAO.java    # Database operations for payments
│           │
│           ├── report/
│           │   └── ReportGenerator.java   # Admin reports
│           │
│           ├── util/
│           │   ├── DBConnection.java  # Shared database connection utility
│           │   └── Validator.java     # Shared input validation helpers
│           │
│           └── Main.java              # Entry point — runs the application
│
├── README.md
└── .gitignore
