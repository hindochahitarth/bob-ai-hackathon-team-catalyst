package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.repository.TempReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ColdChainService {

    private final ShipmentRepository shipmentRepo;
    private final TempReadingRepository tempReadingRepo;

    /** All shipments flagged as cold-chain. */
    public List<Shipment> findColdChainShipments() {
        return shipmentRepo.findByColdChainTrue();
    }

    /**
     * Latest TempReading for a shipment (most recent recordedAt).
     * Returns Optional.empty() if no readings exist yet.
     */
    public Optional<TempReading> latestReading(Shipment shipment) {
        return tempReadingRepo.findByShipment(shipment)
                .stream()
                .filter(r -> r.getRecordedAt() != null)
                .max(Comparator.comparing(TempReading::getRecordedAt));
    }

    /**
     * Determine breach status string based on temperature.
     *
     * Target range: 2 – 8 °C
     *   Normal      : 2 ≤ t ≤ 8
     *   Minor Breach: -2 ≤ t < 2  OR  8 < t ≤ 12
     *   Major Breach: t < -2       OR  t > 12
     */
    public String breachStatus(double tempCelsius) {
        if (tempCelsius >= 2.0 && tempCelsius <= 8.0) {
            return "Normal";
        } else if (tempCelsius >= -2.0 && tempCelsius <= 12.0) {
            return "Minor Breach";
        } else {
            return "Major Breach";
        }
    }
}
