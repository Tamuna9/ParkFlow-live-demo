package com.tamuna.parkflow.web;

import com.tamuna.parkflow.core.ParkingConfig;
import com.tamuna.parkflow.core.ParkingService;
import com.tamuna.parkflow.core.SimulationEngine;
import com.tamuna.parkflow.model.ParkingEvent;
import com.tamuna.parkflow.model.ParkingTicket;
import com.tamuna.parkflow.model.VehicleType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

@Service
@SessionScope
public class SimulationFacade {
    private static final int EVENT_LIMIT = 100;

    private final Object lifecycleLock = new Object();
    private final ArrayDeque<ParkingEvent> recentEvents = new ArrayDeque<>();
    private final SseHub sseHub = new SseHub();

    private volatile ParkingService parkingService;
    private volatile SimulationEngine engine;
    private volatile Status status = Status.READY;
    private volatile double speed = 1.0;
    private volatile long generation;

    @PostConstruct
    public void initialize() {
        reset();
    }

    public DashboardState state() {
        ParkingService service = parkingService;
        return new DashboardState(
                status.name(),
                speed,
                service.snapshot(),
                eventsSnapshot());
    }

    public SseEmitter connect() {
        return sseHub.connect(state());
    }

    public void start() {
        synchronized (lifecycleLock) {
            engine.start();
            status = Status.RUNNING;
        }
        broadcastState();
    }

    public void pause() {
        synchronized (lifecycleLock) {
            engine.pause();
            status = Status.PAUSED;
        }
        broadcastState();
    }

    public void reset() {
        synchronized (lifecycleLock) {
            generation++;
            if (engine != null) {
                engine.close();
            }
            synchronized (recentEvents) {
                recentEvents.clear();
            }

            long sessionGeneration = generation;
            ParkingService newService = new ParkingService(ParkingConfig.defaults());
            SimulationEngine newEngine = new SimulationEngine(newService);
            newEngine.setSpeed(speed);

            newService.addEventListener(event -> {
                if (generation != sessionGeneration) {
                    return;
                }
                remember(event);
                sseHub.broadcastEvent(event);
            });
            newService.addSnapshotListener(snapshot -> {
                if (generation == sessionGeneration) {
                    broadcastState();
                }
            });

            parkingService = newService;
            engine = newEngine;
            status = Status.READY;
            newService.start();
        }
        broadcastState();
    }

    public void setSpeed(double requestedSpeed) {
        speed = Math.max(0.5, Math.min(3.0, requestedSpeed));
        engine.setSpeed(speed);
        broadcastState();
    }

    public void addVehicle(VehicleType type, boolean vip) {
        engine.addManualVehicle(type, vip);
    }

    public Optional<ParkingTicket> releaseSpot(String spotId) {
        return parkingService.releaseSpot(spotId);
    }

    public List<ParkingTicket> history() {
        return parkingService.history();
    }

    private void remember(ParkingEvent event) {
        synchronized (recentEvents) {
            recentEvents.addFirst(event);
            while (recentEvents.size() > EVENT_LIMIT) {
                recentEvents.removeLast();
            }
        }
    }

    private List<ParkingEvent> eventsSnapshot() {
        synchronized (recentEvents) {
            return List.copyOf(recentEvents);
        }
    }

    private void broadcastState() {
        if (parkingService != null) {
            sseHub.broadcastState(state());
        }
    }

    @PreDestroy
    public void close() {
        synchronized (lifecycleLock) {
            if (engine != null) {
                engine.close();
            }
        }
    }

    private enum Status {
        READY, RUNNING, PAUSED
    }
}
