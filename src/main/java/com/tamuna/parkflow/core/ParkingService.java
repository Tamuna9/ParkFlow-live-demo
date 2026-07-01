package com.tamuna.parkflow.core;

import com.tamuna.parkflow.model.ParkingEvent;
import com.tamuna.parkflow.model.ParkingSnapshot;
import com.tamuna.parkflow.model.ParkingSpot;
import com.tamuna.parkflow.model.ParkingTicket;
import com.tamuna.parkflow.model.SpotType;
import com.tamuna.parkflow.model.Vehicle;
import com.tamuna.parkflow.model.VehicleType;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class ParkingService implements AutoCloseable {
    private final ParkingConfig config;
    private final PricingService pricingService;
    private final SimulationClock simulationClock;
    private final Clock clock;
    private final List<ParkingSpot> spots;
    private final LinkedBlockingDeque<Vehicle> incomingQueue;
    private final List<ParkingTicket> history = new ArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ParkingSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ParkingEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock stateLock = new ReentrantLock(true);
    private final Condition spotAvailable = stateLock.newCondition();
    private final AtomicLong idSequence = new AtomicLong(1000);
    private final AtomicReference<Vehicle> awaitingSpot = new AtomicReference<>();
    private final ExecutorService parkingWorker;

    private volatile boolean running;
    private long generated;
    private long parkedTotal;
    private long completed;
    private long rejected;
    private double revenue;

    public ParkingService(ParkingConfig config) {
        this(config, Clock.systemDefaultZone(), new SimulationClock(500));
    }

    ParkingService(ParkingConfig config, Clock clock, SimulationClock simulationClock) {
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.simulationClock = Objects.requireNonNull(simulationClock);
        this.pricingService = new PricingService(config);
        this.incomingQueue = new LinkedBlockingDeque<>(config.queueCapacity());
        this.spots = createSpots(config);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "parking-allocation-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.parkingWorker = Executors.newSingleThreadExecutor(factory);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        parkingWorker.submit(this::allocationLoop);
        publishEvent(ParkingEvent.EventType.INFO, "Parking facility opened");
        publishSnapshot();
    }

    public Vehicle createVehicle(VehicleType type, boolean vip, int plannedStayMinutes) {
        long id = idSequence.incrementAndGet();
        String plate = "PK-%04d".formatted(id);
        return new Vehicle(id, plate, type, vip, clock.instant(), plannedStayMinutes);
    }

    public boolean accept(Vehicle vehicle) {
        Objects.requireNonNull(vehicle);
        stateLock.lock();
        try {
            generated++;
        } finally {
            stateLock.unlock();
        }

        boolean accepted = vehicle.vip()
                ? incomingQueue.offerFirst(vehicle)
                : incomingQueue.offerLast(vehicle);

        if (accepted) {
            publishEvent(ParkingEvent.EventType.ARRIVAL,
                    "%s %s arrived%s".formatted(
                            vehicle.type().icon(),
                            vehicle.licensePlate(),
                            vehicle.vip() ? " · VIP" : ""));
        } else {
            stateLock.lock();
            try {
                rejected++;
            } finally {
                stateLock.unlock();
            }
            publishEvent(ParkingEvent.EventType.WARNING,
                    "%s rejected: entrance queue is full".formatted(vehicle.licensePlate()));
        }
        publishSnapshot();
        return accepted;
    }

    private void allocationLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Vehicle vehicle = incomingQueue.takeFirst();
                awaitingSpot.set(vehicle);
                ParkingSpot selected;

                stateLock.lockInterruptibly();
                try {
                    while (running && (selected = findBestSpot(vehicle).orElse(null)) == null) {
                        spotAvailable.await();
                    }
                    if (!running) {
                        return;
                    }
                    selected = findBestSpot(vehicle).orElseThrow();
                    vehicle.markParked(clock.instant());
                    selected.park(vehicle);
                    parkedTotal++;
                } finally {
                    stateLock.unlock();
                }

                awaitingSpot.set(null);
                publishEvent(ParkingEvent.EventType.PARKED,
                        "%s parked in spot %s".formatted(vehicle.licensePlate(), selected.id()));
                publishSnapshot();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException error) {
                awaitingSpot.set(null);
                publishEvent(ParkingEvent.EventType.WARNING,
                        "Allocation error: " + error.getMessage());
            }
        }
    }

    private Optional<ParkingSpot> findBestSpot(Vehicle vehicle) {
        return spots.stream()
                .filter(ParkingSpot::isFree)
                .filter(spot -> spot.type().accepts(vehicle.type()))
                .min(Comparator
                        .comparingInt((ParkingSpot spot) -> spot.type().matchScore(vehicle.type()))
                        .thenComparing(ParkingSpot::id));
    }

    public int releaseDueVehicles() {
        Instant now = clock.instant();
        List<ParkingTicket> released = new ArrayList<>();
        stateLock.lock();
        try {
            for (ParkingSpot spot : spots) {
                Vehicle occupant = spot.occupant();
                if (occupant != null
                        && simulationClock.stayCompleted(
                        occupant.parkedAt(), occupant.plannedStayMinutes(), now)) {
                    released.add(releaseLocked(spot, now));
                }
            }
            if (!released.isEmpty()) {
                spotAvailable.signalAll();
            }
        } finally {
            stateLock.unlock();
        }
        released.forEach(ticket -> publishEvent(ParkingEvent.EventType.DEPARTURE,
                "%s left spot %s · %.2f GEL".formatted(
                        ticket.licensePlate(), ticket.spotId(), ticket.cost())));
        if (!released.isEmpty()) {
            publishSnapshot();
        }
        return released.size();
    }

    public Optional<ParkingTicket> releaseSpot(String spotId) {
        ParkingTicket ticket = null;
        stateLock.lock();
        try {
            ParkingSpot spot = spots.stream()
                    .filter(candidate -> candidate.id().equalsIgnoreCase(spotId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown parking spot: " + spotId));
            if (!spot.isFree()) {
                ticket = releaseLocked(spot, clock.instant());
                spotAvailable.signalAll();
            }
        } finally {
            stateLock.unlock();
        }
        if (ticket != null) {
            publishEvent(ParkingEvent.EventType.DEPARTURE,
                    "%s checked out manually · %.2f GEL".formatted(
                            ticket.licensePlate(), ticket.cost()));
            publishSnapshot();
        }
        return Optional.ofNullable(ticket);
    }

    private ParkingTicket releaseLocked(ParkingSpot spot, Instant departedAt) {
        Vehicle vehicle = spot.release();
        long minutes = simulationClock.simulatedMinutesBetween(vehicle.parkedAt(), departedAt);
        double cost = pricingService.calculate(vehicle.type(), minutes, vehicle.vip());
        ParkingTicket ticket = new ParkingTicket(
                vehicle.id(),
                vehicle.licensePlate(),
                vehicle.type(),
                spot.id(),
                vehicle.arrivedAt(),
                vehicle.parkedAt(),
                departedAt,
                minutes,
                cost);
        history.add(ticket);
        completed++;
        revenue = Math.round((revenue + cost) * 100.0) / 100.0;
        return ticket;
    }

    public ParkingSnapshot snapshot() {
        stateLock.lock();
        try {
            List<ParkingSnapshot.SpotSnapshot> spotSnapshots = spots.stream()
                    .map(spot -> {
                        Vehicle vehicle = spot.occupant();
                        return new ParkingSnapshot.SpotSnapshot(
                                spot.id(),
                                spot.level(),
                                spot.type(),
                                vehicle != null,
                                vehicle == null ? "" : vehicle.licensePlate(),
                                vehicle == null ? null : vehicle.type(),
                                vehicle != null && vehicle.vip());
                    })
                    .toList();

            List<ParkingSnapshot.VehicleSnapshot> queueSnapshots = new ArrayList<>();
            Vehicle pending = awaitingSpot.get();
            if (pending != null) {
                queueSnapshots.add(toSnapshot(pending));
            }
            incomingQueue.stream().map(this::toSnapshot).forEach(queueSnapshots::add);

            int occupied = (int) spots.stream().filter(spot -> !spot.isFree()).count();
            return new ParkingSnapshot(
                    spotSnapshots,
                    List.copyOf(queueSnapshots),
                    spots.size(),
                    occupied,
                    generated,
                    parkedTotal,
                    completed,
                    rejected,
                    revenue);
        } finally {
            stateLock.unlock();
        }
    }

    private ParkingSnapshot.VehicleSnapshot toSnapshot(Vehicle vehicle) {
        return new ParkingSnapshot.VehicleSnapshot(
                vehicle.id(),
                vehicle.licensePlate(),
                vehicle.type(),
                vehicle.vip());
    }

    public List<ParkingTicket> history() {
        stateLock.lock();
        try {
            return List.copyOf(history);
        } finally {
            stateLock.unlock();
        }
    }

    public void addSnapshotListener(Consumer<ParkingSnapshot> listener) {
        snapshotListeners.add(Objects.requireNonNull(listener));
    }

    public void addEventListener(Consumer<ParkingEvent> listener) {
        eventListeners.add(Objects.requireNonNull(listener));
    }

    private void publishSnapshot() {
        ParkingSnapshot snapshot = snapshot();
        snapshotListeners.forEach(listener -> safelyNotify(listener, snapshot));
    }

    private void publishEvent(ParkingEvent.EventType type, String message) {
        ParkingEvent event = new ParkingEvent(clock.instant(), type, message);
        eventListeners.forEach(listener -> safelyNotify(listener, event));
    }

    private <T> void safelyNotify(Consumer<T> listener, T value) {
        try {
            listener.accept(value);
        } catch (RuntimeException ignored) {
            // A UI listener must never stop the simulation worker.
        }
    }

    private static List<ParkingSpot> createSpots(ParkingConfig config) {
        List<ParkingSpot> result = new ArrayList<>();
        for (int levelIndex = 0; levelIndex < config.levels(); levelIndex++) {
            char level = (char) ('A' + levelIndex);
            for (int number = 1; number <= config.spotsPerLevel(); number++) {
                SpotType type;
                if (number == 1) {
                    type = SpotType.ACCESSIBLE;
                } else if (number <= 3) {
                    type = SpotType.ELECTRIC;
                } else if (number > config.spotsPerLevel() - 2) {
                    type = SpotType.MOTORCYCLE;
                } else {
                    type = SpotType.REGULAR;
                }
                result.add(new ParkingSpot(level, number, type));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void close() {
        running = false;
        stateLock.lock();
        try {
            spotAvailable.signalAll();
        } finally {
            stateLock.unlock();
        }
        parkingWorker.shutdownNow();
        try {
            parkingWorker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
