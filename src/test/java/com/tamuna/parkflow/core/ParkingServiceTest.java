package com.tamuna.parkflow.core;

import com.tamuna.parkflow.model.ParkingSnapshot;
import com.tamuna.parkflow.model.SpotType;
import com.tamuna.parkflow.model.Vehicle;
import com.tamuna.parkflow.model.VehicleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkingServiceTest {
    private ParkingService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void rejectsVehicleWhenBoundedQueueIsFull() {
        ParkingConfig config = new ParkingConfig(1, 12, 2, 35, 45, 20, 25);
        service = new ParkingService(config);

        assertTrue(service.accept(service.createVehicle(VehicleType.CAR, false, 10)));
        assertTrue(service.accept(service.createVehicle(VehicleType.CAR, false, 10)));
        assertFalse(service.accept(service.createVehicle(VehicleType.CAR, false, 10)));

        ParkingSnapshot snapshot = service.snapshot();
        assertEquals(3, snapshot.generated());
        assertEquals(2, snapshot.queue().size());
        assertEquals(1, snapshot.rejected());
    }

    @Test
    void allocatesVehiclesOnlyToCompatibleSpots() throws Exception {
        service = new ParkingService(ParkingConfig.defaults());
        service.start();
        service.accept(service.createVehicle(VehicleType.ACCESSIBLE, false, 20));
        service.accept(service.createVehicle(VehicleType.ELECTRIC, false, 20));
        service.accept(service.createVehicle(VehicleType.MOTORCYCLE, false, 20));
        service.accept(service.createVehicle(VehicleType.CAR, false, 20));

        await(() -> service.snapshot().occupied() == 4);
        ParkingSnapshot snapshot = service.snapshot();

        snapshot.spots().stream()
                .filter(ParkingSnapshot.SpotSnapshot::occupied)
                .forEach(spot -> assertTrue(spot.type().accepts(spot.vehicleType())));
        assertTrue(snapshot.spots().stream().anyMatch(spot ->
                spot.occupied()
                        && spot.vehicleType() == VehicleType.ELECTRIC
                        && spot.type() == SpotType.ELECTRIC));
        assertTrue(snapshot.spots().stream().anyMatch(spot ->
                spot.occupied()
                        && spot.vehicleType() == VehicleType.MOTORCYCLE
                        && spot.type() == SpotType.MOTORCYCLE));
    }

    @Test
    void releasesDueVehicleAndCreatesPaidTicket() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        service = new ParkingService(
                ParkingConfig.defaults(),
                clock,
                new SimulationClock(500));
        service.start();
        Vehicle car = service.createVehicle(VehicleType.CAR, false, 5);
        service.accept(car);
        await(() -> service.snapshot().occupied() == 1);

        clock.advanceMillis(2_500);
        assertEquals(1, service.releaseDueVehicles());
        assertEquals(0, service.snapshot().occupied());
        assertEquals(1, service.snapshot().completed());
        assertTrue(service.snapshot().revenue() > 0);
        assertEquals(1, service.history().size());
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met before timeout");
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
