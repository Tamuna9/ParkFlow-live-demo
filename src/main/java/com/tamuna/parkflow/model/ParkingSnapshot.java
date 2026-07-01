package com.tamuna.parkflow.model;

import java.util.List;

public record ParkingSnapshot(
        List<SpotSnapshot> spots,
        List<VehicleSnapshot> queue,
        int capacity,
        int occupied,
        long generated,
        long parkedTotal,
        long completed,
        long rejected,
        double revenue
) {
    public record SpotSnapshot(
            String id,
            char level,
            SpotType type,
            boolean occupied,
            String licensePlate,
            VehicleType vehicleType,
            boolean vip
    ) {
    }

    public record VehicleSnapshot(
            long id,
            String licensePlate,
            VehicleType type,
            boolean vip
    ) {
    }
}
