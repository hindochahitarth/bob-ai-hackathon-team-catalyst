package com.teamcatalyst.supplychain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamcatalyst.supplychain.model.*;
import com.teamcatalyst.supplychain.repository.*;
import com.teamcatalyst.supplychain.service.ColdChainService;
import com.teamcatalyst.supplychain.service.DisruptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LiveApiController
 *
 * REST + SSE endpoints consumed by the frontend JavaScript for real-time
 * dashboard updates without full page reloads.
 *
 * SSE endpoint  : GET /api/v1/live-stream   (text/event-stream)
 * REST snapshots: GET /api/v1/dashboard/stats
 *                 GET /api/v1/shipments
 *                 GET /api/v1/disruptions
 *                 GET /api/v1/fleet
 *                 GET /api/v1/coldchain
 *                 POST /api/v1/disruptions/{id}/resolve
 *                 POST /api/v1/temperature   (IoT ingest)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LiveApiController {

    private final ShipmentRepository shipmentRepo;
    private final DisruptionEventRepository disruptionRepo;
    private final FleetAssetRepository fleetRepo;
    private final TempReadingRepository tempReadingRepo;
    private final ColdChainService coldChainService;
    private final DisruptionService disruptionService;
    private final ObjectMapper objectMapper;

    /** All active SSE subscribers — thread-safe. */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // SSE stream — clients subscribe and receive JSON events every time the
    // simulation mutates the DB (pushed from LiveSimulationService via this API)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping(value = "/live-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial snapshot immediately
        try {
            emitter.send(SseEmitter.event()
                .name("stats")
                .data(buildDashboardStats()));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Called internally (e.g. from a scheduled task) to push an update to all subscribers. */
    public void broadcastStats() {
        if (emitters.isEmpty()) return;
        try {
            String payload = objectMapper.writeValueAsString(buildDashboardStats());
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter e : emitters) {
                try {
                    e.send(SseEmitter.event().name("stats").data(payload));
                } catch (Exception ex) {
                    dead.add(e);
                }
            }
            emitters.removeAll(dead);
        } catch (Exception ex) {
            log.warn("SSE broadcast error: {}", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard snapshot
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/dashboard/stats")
    public Map<String, Object> dashboardStats() {
        return buildDashboardStats();
    }

    private Map<String, Object> buildDashboardStats() {
        long totalShipments     = shipmentRepo.count();
        long activeDisruptions  = disruptionRepo.findAll().stream()
                                    .filter(d -> "ACTIVE".equals(d.getStatus())).count();
        long idleFleet          = fleetRepo.countByStatus("IDLE");
        long coldChainCount     = shipmentRepo.countByColdChainTrue();
        long delayedShipments   = shipmentRepo.findAll().stream()
                                    .filter(s -> "DELAYED".equalsIgnoreCase(s.getStatus())).count();
        long inTransitShipments = shipmentRepo.findAll().stream()
                                    .filter(s -> s.getStatus() != null && s.getStatus().startsWith("IN_TRANSIT")).count();

        // Cold chain breach counts
        long minorBreaches = 0, majorBreaches = 0;
        for (Shipment s : coldChainService.findColdChainShipments()) {
            var reading = coldChainService.latestReading(s);
            if (reading.isPresent()) {
                String status = coldChainService.breachStatus(reading.get().getTemperatureCelsius());
                if ("Minor Breach".equals(status)) minorBreaches++;
                else if ("Major Breach".equals(status)) majorBreaches++;
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalShipments",     totalShipments);
        stats.put("inTransit",          inTransitShipments);
        stats.put("delayed",            delayedShipments);
        stats.put("activeDisruptions",  activeDisruptions);
        stats.put("idleFleet",          idleFleet);
        stats.put("coldChainCount",     coldChainCount);
        stats.put("minorBreaches",      minorBreaches);
        stats.put("majorBreaches",      majorBreaches);
        stats.put("timestamp",          LocalDateTime.now().toString());
        return stats;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shipments REST
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/shipments")
    public List<Map<String, Object>> shipments() {
        return shipmentRepo.findAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",              s.getId());
            m.put("trackingNumber",  s.getTrackingNumber());
            m.put("cargoType",       s.getCargoType());
            m.put("coldChain",       s.isColdChain());
            m.put("status",          s.getStatus());
            m.put("currentLocation", s.getCurrentLocation());
            if (s.getRoute() != null) {
                m.put("origin",      s.getRoute().getOrigin());
                m.put("destination", s.getRoute().getDestination());
                m.put("carrier",     s.getRoute().getCarrier());
                m.put("distanceKm",  s.getRoute().getDistanceKm());
            }
            return m;
        }).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Disruptions REST
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/disruptions")
    public List<DisruptionEvent> disruptions() {
        return disruptionRepo.findAll();
    }

    @PostMapping("/disruptions/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveDisruption(@PathVariable Long id) {
        return disruptionRepo.findById(id).map(d -> {
            d.setStatus("RESOLVED");
            d.setResolvedAt(LocalDateTime.now());
            disruptionRepo.save(d);
            broadcastStats();
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "id", id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/disruptions")
    public ResponseEntity<DisruptionEvent> createDisruption(@RequestBody Map<String, String> body) {
        DisruptionEvent event = new DisruptionEvent(
            null,
            body.getOrDefault("type", "UNKNOWN"),
            body.getOrDefault("affectedSegment", ""),
            body.getOrDefault("description", ""),
            body.getOrDefault("severity", "MEDIUM"),
            "ACTIVE",
            LocalDateTime.now(),
            null
        );
        DisruptionEvent saved = disruptionRepo.save(event);
        broadcastStats();
        return ResponseEntity.ok(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fleet REST
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/fleet")
    public List<FleetAsset> fleet() {
        return fleetRepo.findAll();
    }

    @PostMapping("/fleet/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignFleetAsset(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return fleetRepo.findById(id).map(asset -> {
            asset.setStatus("IN_USE");
            if (body != null && body.containsKey("location")) {
                asset.setCurrentLocation(body.get("location").toString());
            }
            fleetRepo.save(asset);
            broadcastStats();
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "assetId", id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/fleet/{id}/release")
    public ResponseEntity<Map<String, Object>> releaseFleetAsset(@PathVariable Long id) {
        return fleetRepo.findById(id).map(asset -> {
            asset.setStatus("IDLE");
            fleetRepo.save(asset);
            broadcastStats();
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "assetId", id));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cold Chain REST
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/coldchain")
    public List<Map<String, Object>> coldChain() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Shipment s : coldChainService.findColdChainShipments()) {
            var reading = coldChainService.latestReading(s);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",              s.getId());
            row.put("trackingNumber",  s.getTrackingNumber());
            row.put("cargoType",       s.getCargoType());
            row.put("currentLocation", s.getCurrentLocation());
            row.put("status",          s.getStatus());
            if (reading.isPresent()) {
                row.put("temperature",   reading.get().getTemperatureCelsius());
                row.put("recordedAt",    reading.get().getRecordedAt().toString());
                row.put("breachStatus",  coldChainService.breachStatus(reading.get().getTemperatureCelsius()));
            } else {
                row.put("temperature",   null);
                row.put("breachStatus",  "No Data");
            }
            result.add(row);
        }
        return result;
    }

    /** IoT sensor ingest endpoint — POST a temperature reading for a shipment. */
    @PostMapping("/temperature")
    public ResponseEntity<Map<String, Object>> ingestTemperature(@RequestBody Map<String, Object> body) {
        String trackingNumber = (String) body.get("trackingNumber");
        Object tempRaw = body.get("temperature");
        if (trackingNumber == null || tempRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "trackingNumber and temperature required"));
        }
        double temp;
        try { temp = Double.parseDouble(tempRaw.toString()); }
        catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid temperature value"));
        }

        Shipment shipment = shipmentRepo.findByTrackingNumber(trackingNumber);
        if (shipment == null) {
            return ResponseEntity.notFound().build();
        }

        TempReading reading = new TempReading(null, shipment, temp, LocalDateTime.now());
        tempReadingRepo.save(reading);

        String breachStatus = coldChainService.breachStatus(temp);
        if (!"Normal".equals(breachStatus)) {
            coldChainService.notifyOwner(shipment, breachStatus + " — reading: " + temp + " °C");
        }

        broadcastStats();
        return ResponseEntity.ok(Map.of(
            "success",      true,
            "trackingNumber", trackingNumber,
            "temperature",  temp,
            "breachStatus", breachStatus,
            "recordedAt",   LocalDateTime.now().toString()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recent temperature history for sparklines
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/coldchain/{trackingNumber}/history")
    public List<Map<String, Object>> tempHistory(@PathVariable String trackingNumber) {
        Shipment s = shipmentRepo.findByTrackingNumber(trackingNumber);
        if (s == null) return List.of();
        return tempReadingRepo.findByShipment(s).stream()
            .sorted(Comparator.comparing(TempReading::getRecordedAt))
            .map(r -> Map.<String, Object>of(
                "temperature", r.getTemperatureCelsius(),
                "recordedAt",  r.getRecordedAt().toString()
            ))
            .toList();
    }
}
