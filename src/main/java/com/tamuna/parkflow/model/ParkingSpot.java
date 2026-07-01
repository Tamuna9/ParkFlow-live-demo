package com.tamuna.parkflow.model;

import java.util.Objects;

public final class ParkingSpot {
    private final String id;
    private final char level;
    private final int number;
    private final SpotType type;
    private Vehicle occupant;

    public ParkingSpot(char level, int number, SpotType type) {
        this.level = level;
        this.number = number;
        this.type = Objects.requireNonNull(type);
        this.id = "%c-%02d".formatted(level, number);
    }

    public String id() {
        return id;
    }

    public char level() {
        return level;
    }

    public int number() {
        return number;
    }

    public SpotType type() {
        return type;
    }

    public Vehicle occupant() {
        return occupant;
    }

    public boolean isFree() {
        return occupant == null;
    }

    public void park(Vehicle vehicle) {
        if (!isFree()) {
            throw new IllegalStateException("Spot " + id + " is occupied");
        }
        if (!type.accepts(vehicle.type())) {
            throw new IllegalArgumentException("Incompatible vehicle type");
        }
        occupant = Objects.requireNonNull(vehicle);
    }

    public Vehicle release() {
        if (isFree()) {
            throw new IllegalStateException("Spot " + id + " is already free");
        }
        Vehicle released = occupant;
        occupant = null;
        return released;
    }
}
