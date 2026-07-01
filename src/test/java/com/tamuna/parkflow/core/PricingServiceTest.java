package com.tamuna.parkflow.core;

import com.tamuna.parkflow.model.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingServiceTest {
    private final PricingService pricing = new PricingService(ParkingConfig.defaults());

    @Test
    void calculatesDifferentRatesAndVipSurcharge() {
        assertEquals(35.00, pricing.calculate(VehicleType.CAR, 60, false));
        assertEquals(45.00, pricing.calculate(VehicleType.ELECTRIC, 60, false));
        assertEquals(42.00, pricing.calculate(VehicleType.CAR, 60, true));
        assertEquals(0.58, pricing.calculate(VehicleType.CAR, 0, false));
    }
}
