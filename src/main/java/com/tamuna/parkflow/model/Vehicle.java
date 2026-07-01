package com.tamuna.parkflow.model;

import java.time.Instant;
import java.util.Objects;

public final class Vehicle {
    private final long id;
    private final String licensePlate;
    private final VehicleType type;
    private final boolean vip;
    private final Instant arrivedAt;
    private final int plannedStayMinutes;
    private volatile Instant parkedAt;

    public Vehicle(long id, String licensePlate, VehicleType type, boolean vip,
                   Instant arrivedAt, int plannedStayMinutes) {
        this.id = id;
        this.licensePlate = Objects.requireNonNull(licensePlate);
        this.type = Objects.requireNonNull(type);
        this.vip = vip;
        this.arrivedAt = Objects.requireNonNull(arrivedAt);
        this.plannedStayMinutes = plannedStayMinutes;
    }

    public long id() {
        return id;
    }

    public String licensePlate() {
        return licensePlate;
    }

    public VehicleType type() {
        return type;
    }

    public boolean vip() {
        return vip;
    }

    public Instant arrivedAt() {
        return arrivedAt;
    }

    public int plannedStayMinutes() {
        return plannedStayMinutes;
    }

    public Instant parkedAt() {
        return parkedAt;
    }

    public void markParked(Instant time) {
        if (parkedAt != null) {
            throw new IllegalStateException("Vehicle is already parked");
        }
        parkedAt = Objects.requireNonNull(time);
    }
}
