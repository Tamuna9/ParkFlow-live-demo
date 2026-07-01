package com.tamuna.parkflow.model;

public enum VehicleType {
    CAR("Car", "🚗"),
    ELECTRIC("Electric vehicle", "⚡"),
    MOTORCYCLE("Motorcycle", "🏍"),
    ACCESSIBLE("Accessible vehicle", "♿");

    private final String title;
    private final String icon;

    VehicleType(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    public String title() {
        return title;
    }

    public String icon() {
        return icon;
    }
}
