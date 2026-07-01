package com.tamuna.parkflow.core;

import java.time.Duration;
import java.time.Instant;

public final class SimulationClock {
    private final long realMillisPerSimulatedMinute;

    public SimulationClock(long realMillisPerSimulatedMinute) {
        if (realMillisPerSimulatedMinute < 1) {
            throw new IllegalArgumentException("Clock speed must be positive");
        }
        this.realMillisPerSimulatedMinute = realMillisPerSimulatedMinute;
    }

    public long simulatedMinutesBetween(Instant start, Instant end) {
        long millis = Math.max(0, Duration.between(start, end).toMillis());
        return Math.max(1, millis / realMillisPerSimulatedMinute);
    }

    public boolean stayCompleted(Instant parkedAt, int plannedMinutes, Instant now) {
        return simulatedMinutesBetween(parkedAt, now) >= plannedMinutes;
    }
}
