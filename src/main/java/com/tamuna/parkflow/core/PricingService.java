package com.tamuna.parkflow.core;

import com.tamuna.parkflow.model.VehicleType;

public final class PricingService {
    private final ParkingConfig config;

    public PricingService(ParkingConfig config) {
        this.config = config;
    }

    public double calculate(VehicleType type, long simulatedMinutes, boolean vip) {
        double hourlyRate = switch (type) {
            case CAR -> config.regularHourlyRate();
            case ELECTRIC -> config.electricHourlyRate();
            case MOTORCYCLE -> config.motorcycleHourlyRate();
            case ACCESSIBLE -> config.accessibleHourlyRate();
        };
        double subtotal = hourlyRate * Math.max(1, simulatedMinutes) / 60.0;
        double multiplier = vip ? 1.20 : 1.0;
        return Math.round(subtotal * multiplier * 100.0) / 100.0;
    }
}
