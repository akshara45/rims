package com.rental.user;

import java.time.LocalDate;

public class User {
    private String userId;
    private String name;
    private String email;
    private String role; // "CUSTOMER" or "ADMIN"
    private String phone;
    private String password;
    private String createdAt;

    public User(String userId, String name, String email) {
        this(userId, name, email, "CUSTOMER", "", "", LocalDate.now().toString());
    }

    public User(String userId, String name, String email, String role, String phone) {
        this(userId, name, email, role, phone, "", LocalDate.now().toString());
    }

    public User(String userId, String name, String email, String role, String phone, String password) {
        this(userId, name, email, role, phone, password, LocalDate.now().toString());
    }

    public User(String userId, String name, String email, String role, String phone, String password, String createdAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = (role == null || role.trim().isEmpty()) ? "CUSTOMER" : role.toUpperCase();
        this.phone = phone != null ? phone : "";
        this.password = password != null ? password : "";
        this.createdAt = createdAt != null ? createdAt : LocalDate.now().toString();
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getPhone() { return phone; }
    public String getPassword() { return password; }
    public String getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = role; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setPassword(String password) { this.password = password; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    @Override
    public String toString() {
        return "User{id=" + userId + ", name=" + name + ", email=" + email + ", role=" + role + "}";
    }
}