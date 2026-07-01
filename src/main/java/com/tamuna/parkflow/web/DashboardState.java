package com.tamuna.parkflow.web;

import com.tamuna.parkflow.model.ParkingEvent;
import com.tamuna.parkflow.model.ParkingSnapshot;

import java.util.List;

public record DashboardState(
        String status,
        double speed,
        ParkingSnapshot parking,
        List<ParkingEvent> recentEvents
) {
}
