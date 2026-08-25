package com.rental.item;

public class Item {
    private String itemId;
    private String name;
    private double rentalPrice;
    private boolean available;

    public Item(String itemId, String name, double rentalPrice) {
        this.itemId = itemId;
        this.name = name;
        this.rentalPrice = rentalPrice;
        this.available = true;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getRentalPrice() { return rentalPrice; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    @Override
    public String toString() {
        return "Item{id=" + itemId + ", name=" + name + ", price=" + rentalPrice + ", available=" + available + "}";
    }
}