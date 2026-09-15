package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.repository.TempReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
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
     *   Normal      : 2.0 ≤ t ≤ 8.0
     *   Minor Breach: 0.0 ≤ t < 2.0  OR  8.0 < t ≤ 10.0
     *   Major Breach: t < 0.0        OR  t > 10.0
     */
    public String breachStatus(double tempCelsius) {
        if (tempCelsius >= 2.0 && tempCelsius <= 8.0) {
            return "Normal";
        } else if ((tempCelsius >= 0.0 && tempCelsius < 2.0) || (tempCelsius > 8.0 && tempCelsius <= 10.0)) {
            return "Minor Breach";
        } else {
            return "Major Breach";
        }
    }

    /**
     * Simulates sending a notification to the shipment owner.
     * Logs the event; in production this would call an email/SMS gateway.
     *
     * @param shipment the shipment whose owner should be notified
     * @param issue    a short description of the issue (e.g. breach status)
     * @return true if owner contact details are present and notification was "sent"
     */
    public boolean notifyOwner(Shipment shipment, String issue) {
        if (shipment.getOwnerEmail() == null || shipment.getOwnerEmail().isBlank()) {
            log.warn("Cannot notify owner for shipment {} — no contact details on record.",
                    shipment.getTrackingNumber());
            return false;
        }
        log.info("[NOTIFY] Shipment: {} | Issue: {} | Owner: {} <{}> {} — notification dispatched.",
                shipment.getTrackingNumber(),
                issue,
                shipment.getOwnerName(),
                shipment.getOwnerEmail(),
                shipment.getOwnerPhone());
        return true;
    }
}
