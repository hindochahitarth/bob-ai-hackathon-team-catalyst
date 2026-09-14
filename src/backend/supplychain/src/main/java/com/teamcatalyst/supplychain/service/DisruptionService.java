package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisruptionService {

    private final DisruptionEventRepository disruptionRepo;
    private final ShipmentRepository shipmentRepo;

    /** Return all disruption events. */
    public List<DisruptionEvent> findAll() {
        return disruptionRepo.findAll();
    }

    /** Return one disruption by id, or empty. */
    public Optional<DisruptionEvent> findById(Long id) {
        return disruptionRepo.findById(id);
    }

    /**
     * Find shipments whose route's pathSegments contain the disruption's
     * affectedSegment (case-insensitive substring match).
     */
    public List<Shipment> findAffectedShipments(DisruptionEvent event) {
        if (event.getAffectedSegment() == null || event.getAffectedSegment().isBlank()) {
            return List.of();
        }
        String segment = event.getAffectedSegment().toLowerCase();
        return shipmentRepo.findAll().stream()
                .filter(s -> s.getRoute() != null
                        && s.getRoute().getPathSegments() != null
                        && s.getRoute().getPathSegments().toLowerCase().contains(segment))
                .toList();
    }

    /**
     * Simple reroute suggestion based on severity and cargo type.
     */
    public String suggestAction(DisruptionEvent event, Shipment shipment) {
        String severity = event.getSeverity() == null ? "" : event.getSeverity().toUpperCase();
        String cargo    = shipment.getCargoType() == null ? "cargo" : shipment.getCargoType();
        return switch (severity) {
            case "HIGH"   -> "🚨 Immediately reroute via alternate corridor. Notify carrier for " + cargo + ".";
            case "MEDIUM" -> "⚠️ Monitor delay; if > 6h, reroute and update ETA for " + cargo + ".";
            default       -> "ℹ️ Low impact — standard delay buffer applies for " + cargo + ".";
        };
    }
}
