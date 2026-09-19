package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.controller.LiveApiController;
import com.teamcatalyst.supplychain.model.*;
import com.teamcatalyst.supplychain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LiveSimulationService
 *
 * Continuously mutates the live database to simulate a real-world supply
 * chain in motion:
 *  - Every 8 s : push a new temperature reading for each cold-chain shipment
 *  - Every 15 s: advance one in-transit shipment to its next waypoint
 *  - Every 30 s: randomly flip an IDLE fleet asset to IN_USE or vice-versa
 *  - Every 60 s: randomly resolve one LOW-severity disruption or raise a new one
 *
 * All mutations are persisted to H2 (or any configured DB) so the REST API
 * and SSE stream always return fresh data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSimulationService {

    private final ShipmentRepository shipmentRepo;
    private final TempReadingRepository tempReadingRepo;
    private final FleetAssetRepository fleetRepo;
    private final DisruptionEventRepository disruptionRepo;
    private final RouteSegmentRepository segmentRepo;

    @Lazy
    @Autowired
    private LiveApiController liveApiController;

    // Ordered waypoint chains for each of the 6 routes
    private static final Map<String, List<String>> ROUTE_WAYPOINTS = Map.of(
        "Mumbai-Delhi",       List.of("MUMBAI_PORT","SURAT_HUB","JAIPUR_CORRIDOR","DELHI_HUB"),
        "Chennai-Pune",       List.of("CHENNAI_PORT","BANGALORE_RING","PUNE_DEPOT"),
        "Surat-Bangalore",    List.of("SURAT_HUB","MUMBAI_BYPASS","PUNE_DEPOT","BANGALORE_RING"),
        "Kolkata-Mumbai",     List.of("KOLKATA_PORT","NAGPUR_CROSSING","NASHIK_HUB","MUMBAI_PORT"),
        "Delhi-Bangalore",    List.of("DELHI_HUB","GWALIOR","NAGPUR_CROSSING","HYDERABAD_HUB","BANGALORE_RING"),
        "Ahmedabad-Kochi",    List.of("AHMEDABAD_HUB","SURAT_HUB","MUMBAI_BYPASS","GOA_COASTAL","KOCHI_PORT")
    );

    private static final List<String> LIVE_DISRUPTION_DESCRIPTIONS = List.of(
        "Flash floods reported on highway segment — convoy hold advised.",
        "Unexpected cargo inspection at border checkpoint, 4-6 hour delay.",
        "Truck breakdown blocking single-lane section, clearance in progress.",
        "Fuel shortage at mid-route depot, refuelling queue building.",
        "Bridge weight restriction enforced, heavy cargo must detour.",
        "Protest roadblock near industrial zone, alternate route activated."
    );

    private static final List<String> DISRUPTION_TYPES = List.of(
        "WEATHER","ROAD","PORT_CONGESTION","INSPECTION","BREAKDOWN"
    );

    private static final List<String> SEGMENTS = List.of(
        "NH48","NH53","NH44","NH66","NAGPUR_CROSSING","SURAT_HUB"
    );

    private static final List<String> INCIDENT_TYPES =
        List.of("CONGESTION", "ACCIDENT", "WEATHER", "ROADWORKS");

    private static final List<String> CONDITION_DESCS = List.of(
        "Heavy truck convoy slowing traffic — expect 25 min delay.",
        "Multi-vehicle accident cleared to one lane — moderate delays.",
        "Monsoon rain reducing visibility — slow convoy in effect.",
        "Road repair crew active — single-lane alternating traffic.",
        "Bridge inspection in progress — weight restriction enforced.",
        "Dense fog advisory — reduced speed limit to 40 km/h.",
        "Fuel tanker spillage — emergency clean-up, partial closure.",
        "Protest rally near junction — alternate detour activated."
    );

    private final Random rng = new Random();

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Temperature simulation — every 8 seconds
    // ──────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 8000)
    @Transactional
    public void pushTemperatureReadings() {
        List<Shipment> coldChain = shipmentRepo.findByColdChainTrue();
        for (Shipment s : coldChain) {
            if ("DELIVERED".equalsIgnoreCase(s.getStatus())) continue;
            double baseTemp = getBaseTemp(s.getTrackingNumber());
            double noise = (rng.nextDouble() - 0.5) * 1.6;
            double spike = rng.nextInt(20) == 0 ? (rng.nextDouble() * 6.0 - 3.0) : 0.0;
            double newTemp = Math.round((baseTemp + noise + spike) * 10.0) / 10.0;
            newTemp = Math.max(-2.0, Math.min(18.0, newTemp));
            TempReading reading = new TempReading(null, s, newTemp, LocalDateTime.now());
            tempReadingRepo.save(reading);
        }
        liveApiController.broadcastStats();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Shipment location advancement — every 15 seconds
    // ──────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void advanceShipmentLocations() {
        List<Shipment> inTransit = shipmentRepo.findAll().stream()
            .filter(s -> "IN_TRANSIT".equalsIgnoreCase(s.getStatus())
                      || "IN_TRANSIT (REROUTED)".equalsIgnoreCase(s.getStatus()))
            .toList();

        if (inTransit.isEmpty()) return;

        // Advance a random subset (up to 3 shipments) each tick
        List<Shipment> toAdvance = new ArrayList<>(inTransit);
        Collections.shuffle(toAdvance, rng);
        int count = Math.min(3, toAdvance.size());

        for (int i = 0; i < count; i++) {
            Shipment s = toAdvance.get(i);
            advanceLocation(s);
            shipmentRepo.save(s);
        }
    }

    private void advanceLocation(Shipment s) {
        if (s.getRoute() == null) return;
        String routeKey = s.getRoute().getOrigin() + "-" + s.getRoute().getDestination();
        List<String> waypoints = ROUTE_WAYPOINTS.get(routeKey);
        if (waypoints == null || waypoints.isEmpty()) return;

        String current = s.getCurrentLocation();
        // Find a human-readable waypoint match
        int idx = -1;
        for (int i = 0; i < waypoints.size(); i++) {
            if (current != null && current.toUpperCase().contains(waypoints.get(i).replace("_", " ").split(" ")[0])) {
                idx = i;
                break;
            }
        }

        int nextIdx = (idx >= 0 && idx < waypoints.size() - 1) ? idx + 1 : rng.nextInt(waypoints.size());
        String nextWaypoint = waypoints.get(nextIdx);

        // If reaching final destination, mark as DELIVERED with a chance
        if (nextIdx == waypoints.size() - 1 && rng.nextInt(8) == 0) {
            s.setStatus("DELIVERED");
            s.setCurrentLocation(humanise(nextWaypoint) + " [Delivered]");
        } else {
            s.setCurrentLocation(humanise(nextWaypoint));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Fleet asset churn — every 30 seconds
    // ──────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void churnFleetAssets() {
        List<FleetAsset> all = fleetRepo.findAll();
        if (all.isEmpty()) return;

        // Flip 1-2 assets
        Collections.shuffle(all, rng);
        int count = Math.min(2, all.size());
        for (int i = 0; i < count; i++) {
            FleetAsset asset = all.get(i);
            if ("IDLE".equals(asset.getStatus())) {
                asset.setStatus("IN_USE");
                log.debug("Fleet {} moved to IN_USE", asset.getIdentifier());
            } else if ("IN_USE".equals(asset.getStatus()) && rng.nextInt(3) == 0) {
                asset.setStatus("IDLE");
                log.debug("Fleet {} returned to IDLE", asset.getIdentifier());
            }
            fleetRepo.save(asset);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Disruption lifecycle — every 60 seconds
    // ──────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void evolveDisruptions() {
        List<DisruptionEvent> all = disruptionRepo.findAll();

        // Occasionally resolve a LOW disruption
        all.stream()
            .filter(d -> "LOW".equalsIgnoreCase(d.getSeverity())
                      && "ACTIVE".equals(d.getStatus()))
            .findFirst()
            .ifPresent(d -> {
                d.setStatus("RESOLVED");
                d.setResolvedAt(LocalDateTime.now());
                disruptionRepo.save(d);
                log.info("Disruption {} auto-resolved", d.getId());
            });

        // Occasionally create a new disruption if there are fewer than 6 active
        long activeCount = all.stream().filter(d -> "ACTIVE".equals(d.getStatus())).count();
        if (activeCount < 6 && rng.nextInt(3) == 0) {
            String type    = DISRUPTION_TYPES.get(rng.nextInt(DISRUPTION_TYPES.size()));
            String segment = SEGMENTS.get(rng.nextInt(SEGMENTS.size()));
            String desc    = LIVE_DISRUPTION_DESCRIPTIONS.get(rng.nextInt(LIVE_DISRUPTION_DESCRIPTIONS.size()));
            String sev     = rng.nextInt(4) == 0 ? "HIGH" : (rng.nextInt(2) == 0 ? "MEDIUM" : "LOW");

            DisruptionEvent newEvent = new DisruptionEvent(
                null, type, segment, desc, sev, "ACTIVE",
                LocalDateTime.now(), null
            );
            disruptionRepo.save(newEvent);
            log.info("Live disruption created: {} on {} [{}]", type, segment, sev);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Per-segment congestion drift — every 10 seconds
    // ──────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void simulateSegmentConditions() {
        List<RouteSegment> segments = segmentRepo.findAll();
        if (segments.isEmpty()) return;

        // Brownian-motion drift on every segment's congestion score
        for (RouteSegment seg : segments) {
            if (seg.isBlocked()) continue; // incidents are handled separately
            double drift = (rng.nextDouble() - 0.48) * 0.08; // slight upward bias
            double newScore = Math.max(0.0, Math.min(1.0,
                    seg.getCongestionScore() + drift));
            seg.setCongestionScore(Math.round(newScore * 100.0) / 100.0);

            // Update speed inversely proportional to congestion
            double freeFlowSpeed = 80.0;
            double speed = Math.max(20.0, freeFlowSpeed * (1.0 - seg.getCongestionScore() * 0.7));
            seg.setSpeedKmh(Math.round(speed * 10.0) / 10.0);

            // Update condition description for high congestion
            if (seg.getCongestionScore() >= 0.6 && "CLEAR".equals(seg.getIncidentType())) {
                seg.setIncidentType("CONGESTION");
                seg.setLiveConditionDesc("Heavy congestion detected — congestion score: "
                    + String.format("%.0f", seg.getCongestionScore() * 100) + "%");
            } else if (seg.getCongestionScore() < 0.3 && "CONGESTION".equals(seg.getIncidentType())) {
                seg.setIncidentType("CLEAR");
                seg.setLiveConditionDesc("Traffic flowing normally.");
            }
            seg.setLastUpdated(LocalDateTime.now());
        }
        segmentRepo.saveAll(segments);

        // Randomly create a new accident/weather incident on a clear segment (3% chance per segment)
        List<RouteSegment> clearSegs = segments.stream()
            .filter(s -> !s.isBlocked() && "CLEAR".equals(s.getIncidentType()))
            .collect(Collectors.toList());
        if (!clearSegs.isEmpty() && rng.nextInt(100) < 3) {
            RouteSegment target = clearSegs.get(rng.nextInt(clearSegs.size()));
            String incident = INCIDENT_TYPES.get(rng.nextInt(INCIDENT_TYPES.size()));
            String desc = CONDITION_DESCS.get(rng.nextInt(CONDITION_DESCS.size()));
            boolean hardBlock = "ACCIDENT".equals(incident) && rng.nextInt(5) == 0;
            target.setIncidentType(incident);
            target.setBlocked(hardBlock);
            target.setCongestionScore(Math.min(1.0, target.getCongestionScore() + 0.3 + rng.nextDouble() * 0.3));
            target.setAdditionalDelayMinutes(10 + rng.nextInt(50));
            target.setLiveConditionDesc(desc);
            target.setSpeedKmh(hardBlock ? 0.0 : 20.0 + rng.nextDouble() * 20.0);
            target.setLastUpdated(LocalDateTime.now());
            segmentRepo.save(target);
            log.info("Incident created: {} on {} ({} → {})",
                incident, target.getSegmentId(), target.getFromNode(), target.getToNode());
        }

        // Randomly recover a blocked or incident segment (5% chance per incident segment)
        List<RouteSegment> incidentSegs = segments.stream()
            .filter(s -> !"CLEAR".equals(s.getIncidentType()))
            .collect(Collectors.toList());
        if (!incidentSegs.isEmpty() && rng.nextInt(100) < 5) {
            RouteSegment recovering = incidentSegs.get(rng.nextInt(incidentSegs.size()));
            recovering.setIncidentType("CLEAR");
            recovering.setBlocked(false);
            recovering.setCongestionScore(Math.max(0.0, recovering.getCongestionScore() - 0.4));
            recovering.setAdditionalDelayMinutes(0);
            recovering.setLiveConditionDesc("Incident cleared — normal operations resumed.");
            recovering.setSpeedKmh(60.0 + rng.nextDouble() * 20.0);
            recovering.setLastUpdated(LocalDateTime.now());
            segmentRepo.save(recovering);
            log.info("Segment recovered: {} ({} → {})",
                recovering.getSegmentId(), recovering.getFromNode(), recovering.getToNode());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns a stable base temperature per shipment so drift is smooth. */
    private double getBaseTemp(String trackingNumber) {
        // Pull from the tracking number hash to get a consistent base
        int hash = Math.abs(trackingNumber.hashCode());
        double base = 3.0 + (hash % 60) / 20.0; // 3.0 – 6.0°C nominal
        return base;
    }

    /** Converts GRAPH_NODE_ID to a human-readable city name for display. */
    public static String humanise(String nodeId) {
        if (nodeId == null) return "Unknown";
        return switch (nodeId.toUpperCase()) {
            case "MUMBAI_PORT"     -> "Mumbai Port";
            case "MUMBAI_BYPASS"   -> "Mumbai Bypass";
            case "SURAT_HUB"       -> "Surat Hub";
            case "JAIPUR_CORRIDOR" -> "Jaipur Corridor";
            case "DELHI_HUB"       -> "Delhi Hub";
            case "CHENNAI_PORT"    -> "Chennai Port";
            case "BANGALORE_RING"  -> "Bangalore Ring Road";
            case "PUNE_DEPOT"      -> "Pune Depot";
            case "KOLKATA_PORT"    -> "Kolkata Port";
            case "NAGPUR_CROSSING" -> "Nagpur Crossing";
            case "NASHIK_HUB"      -> "Nashik Hub";
            case "GWALIOR"         -> "Gwalior";
            case "HYDERABAD_HUB"   -> "Hyderabad Hub";
            case "AHMEDABAD_HUB"   -> "Ahmedabad Hub";
            case "GOA_COASTAL"     -> "Goa Coastal";
            case "KOCHI_PORT"      -> "Kochi Port";
            default -> nodeId.replace("_", " ");
        };
    }
}
