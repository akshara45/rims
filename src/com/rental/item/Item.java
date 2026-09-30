package com.rental.item;

public class Item {
    private String itemId;
    private String name;
    private String category;
    private String description;
    private double rentalPrice;
    private boolean available;
    private String imageUrl;

    public Item(String itemId, String name, double rentalPrice) {
        this(itemId, name, "General", "", rentalPrice, true, "");
    }

    public Item(String itemId, String name, String category, String description, double rentalPrice) {
        this(itemId, name, category, description, rentalPrice, true, "");
    }

    public Item(String itemId, String name, String category, String description, double rentalPrice, boolean available, String imageUrl) {
        this.itemId = itemId;
        this.name = name;
        this.category = (category == null || category.trim().isEmpty()) ? "General" : category;
        this.description = description != null ? description : "";
        this.rentalPrice = rentalPrice;
        this.available = available;
        this.imageUrl = imageUrl != null ? imageUrl : "";
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public double getRentalPrice() { return rentalPrice; }
    public boolean isAvailable() { return available; }
    public String getImageUrl() { return imageUrl; }

    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setDescription(String description) { this.description = description; }
    public void setRentalPrice(double rentalPrice) { this.rentalPrice = rentalPrice; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    @Override
    public String toString() {
        return "Item{id=" + itemId + ", name=" + name + ", category=" + category + ", price=" + rentalPrice + ", available=" + available + "}";
    }
}