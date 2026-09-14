package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import com.teamcatalyst.supplychain.model.RerouteResult;
import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DisruptionService {

    private final DisruptionEventRepository disruptionRepo;
    private final ShipmentRepository shipmentRepo;
    private final RouteOptimizationService routeOptimizationService;

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
     * Calculates an optimal bypass reroute using Dijkstra's shortest-path algorithm
     * on the commercial logistics graph.
     */
    public RerouteResult suggestReroute(DisruptionEvent event, Shipment shipment) {
        if (shipment.getRoute() == null) {
            return RerouteResult.builder()
                    .success(false)
                    .summary("Shipment has no assigned route.")
                    .build();
        }

        String origin = shipment.getRoute().getOrigin();
        String destination = shipment.getRoute().getDestination();
        String affected = event.getAffectedSegment();

        Set<String> blockedSegments = new HashSet<>();
        Set<String> blockedNodes = new HashSet<>();

        if (affected != null && !affected.isBlank()) {
            String clean = affected.trim().toUpperCase();
            // Check if affected item is a node or a segment
            if (clean.endsWith("_PORT") || clean.endsWith("_HUB") || clean.endsWith("_DEPOT")) {
                blockedNodes.add(clean);
            } else {
                blockedSegments.add(clean);
            }
        }

        return routeOptimizationService.findOptimalRoute(
                origin,
                destination,
                blockedSegments,
                blockedNodes,
                shipment.getCargoType()
        );
    }
}
