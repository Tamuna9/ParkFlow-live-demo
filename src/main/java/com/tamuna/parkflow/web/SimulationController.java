package com.tamuna.parkflow.web;

import com.tamuna.parkflow.model.ParkingTicket;
import com.tamuna.parkflow.model.VehicleType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public final class SimulationController {
    private final SimulationFacade simulation;

    public SimulationController(SimulationFacade simulation) {
        this.simulation = simulation;
    }

    @GetMapping("/state")
    public DashboardState state() {
        return simulation.state();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return simulation.connect();
    }

    @PostMapping("/simulation/start")
    public DashboardState start() {
        simulation.start();
        return simulation.state();
    }

    @PostMapping("/simulation/pause")
    public DashboardState pause() {
        simulation.pause();
        return simulation.state();
    }

    @PostMapping("/simulation/reset")
    public DashboardState reset() {
        simulation.reset();
        return simulation.state();
    }

    @PostMapping("/simulation/speed")
    public DashboardState speed(@RequestBody SpeedRequest request) {
        simulation.setSpeed(request.speed());
        return simulation.state();
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> addVehicle(@RequestBody VehicleRequest request) {
        simulation.addVehicle(request.type(), request.vip());
        return Map.of("status", "accepted");
    }

    @PostMapping("/spots/{spotId}/release")
    public ParkingTicket release(@PathVariable String spotId) {
        return simulation.releaseSpot(spotId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Parking spot " + spotId + " is already available"));
    }

    @GetMapping("/history")
    public List<ParkingTicket> history() {
        return simulation.history();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidRequest(IllegalArgumentException error) {
        return Map.of("error", error.getMessage());
    }

    public record SpeedRequest(double speed) {
    }

    public record VehicleRequest(VehicleType type, boolean vip) {
    }
}
