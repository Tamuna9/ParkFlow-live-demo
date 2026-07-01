package com.tamuna.parkflow.core;

public record ParkingConfig(
        int levels,
        int spotsPerLevel,
        int queueCapacity,
        double regularHourlyRate,
        double electricHourlyRate,
        double motorcycleHourlyRate,
        double accessibleHourlyRate
) {
    public ParkingConfig {
        if (levels < 1 || levels > 10) {
            throw new IllegalArgumentException("levels must be between 1 and 10");
        }
        if (spotsPerLevel < 4) {
            throw new IllegalArgumentException("spotsPerLevel must be at least 4");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
    }

    public static ParkingConfig defaults() {
        return new ParkingConfig(3, 12, 10, 35, 45, 20, 25);
    }
}
