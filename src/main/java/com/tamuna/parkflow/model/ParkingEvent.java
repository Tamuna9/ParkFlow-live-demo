package com.tamuna.parkflow.model;

import java.time.Instant;

public record ParkingEvent(Instant time, EventType type, String message) {
    public enum EventType {
        INFO, ARRIVAL, PARKED, DEPARTURE, WARNING
    }
}
