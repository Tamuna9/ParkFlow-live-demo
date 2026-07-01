package com.tamuna.parkflow.model;

import java.time.Instant;

public record ParkingTicket(
        long vehicleId,
        String licensePlate,
        VehicleType vehicleType,
        String spotId,
        Instant arrivedAt,
        Instant parkedAt,
        Instant departedAt,
        long simulatedMinutes,
        double cost
) {
}
