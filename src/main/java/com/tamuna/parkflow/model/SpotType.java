package com.tamuna.parkflow.model;

public enum SpotType {
    REGULAR("Regular"),
    ELECTRIC("EV charging"),
    MOTORCYCLE("Motorcycle"),
    ACCESSIBLE("Accessible");

    private final String title;

    SpotType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public boolean accepts(VehicleType vehicleType) {
        return switch (vehicleType) {
            case CAR -> this == REGULAR;
            case ELECTRIC -> this == ELECTRIC || this == REGULAR;
            case MOTORCYCLE -> this == MOTORCYCLE || this == REGULAR;
            case ACCESSIBLE -> this == ACCESSIBLE;
        };
    }

    public int matchScore(VehicleType vehicleType) {
        if (!accepts(vehicleType)) {
            return Integer.MAX_VALUE;
        }
        return switch (vehicleType) {
            case ELECTRIC -> this == ELECTRIC ? 0 : 1;
            case MOTORCYCLE -> this == MOTORCYCLE ? 0 : 1;
            default -> 0;
        };
    }
}
