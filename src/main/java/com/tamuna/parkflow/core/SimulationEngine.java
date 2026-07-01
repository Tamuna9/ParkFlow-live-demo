package com.tamuna.parkflow.core;

import com.tamuna.parkflow.model.Vehicle;
import com.tamuna.parkflow.model.VehicleType;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SimulationEngine implements AutoCloseable {
    private final ParkingService parkingService;
    private final AtomicBoolean paused = new AtomicBoolean(true);
    private final AtomicLong nextArrivalAt = new AtomicLong();
    private ScheduledExecutorService scheduler;
    private volatile double speed = 1.0;

    public SimulationEngine(ParkingService parkingService) {
        this.parkingService = Objects.requireNonNull(parkingService);
    }

    public synchronized void start() {
        if (scheduler == null) {
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "simulation-scheduler");
                thread.setDaemon(true);
                return thread;
            };
            scheduler = Executors.newScheduledThreadPool(2, factory);
            parkingService.start();
            nextArrivalAt.set(System.currentTimeMillis());
            scheduler.scheduleAtFixedRate(this::generateWhenDue, 0, 100, TimeUnit.MILLISECONDS);
            scheduler.scheduleAtFixedRate(
                    () -> {
                        if (!paused.get()) {
                            parkingService.releaseDueVehicles();
                        }
                    },
                    250, 250, TimeUnit.MILLISECONDS);
        }
        paused.set(false);
    }

    public void pause() {
        paused.set(true);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(0.5, Math.min(3.0, speed));
    }

    public void addManualVehicle(VehicleType type, boolean vip) {
        Vehicle vehicle = parkingService.createVehicle(
                type,
                vip,
                ThreadLocalRandom.current().nextInt(5, 18));
        parkingService.accept(vehicle);
    }

    private void generateWhenDue() {
        if (paused.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextArrivalAt.get()) {
            return;
        }
        VehicleType type = randomVehicleType();
        boolean vip = ThreadLocalRandom.current().nextDouble() < 0.12;
        addManualVehicle(type, vip);

        long delay = (long) (ThreadLocalRandom.current().nextLong(650, 1_500) / speed);
        nextArrivalAt.set(now + delay);
    }

    private VehicleType randomVehicleType() {
        double value = ThreadLocalRandom.current().nextDouble();
        if (value < 0.58) {
            return VehicleType.CAR;
        }
        if (value < 0.78) {
            return VehicleType.ELECTRIC;
        }
        if (value < 0.94) {
            return VehicleType.MOTORCYCLE;
        }
        return VehicleType.ACCESSIBLE;
    }

    @Override
    public synchronized void close() {
        paused.set(true);
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        parkingService.close();
    }
}
