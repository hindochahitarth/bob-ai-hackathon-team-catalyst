package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.FleetAssetRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.repository.TempReadingRepository;
import com.teamcatalyst.supplychain.service.ColdChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DashboardController — Home page KPI aggregation.
 *
 * All KPIs are computed via single-query DB aggregations (count/exists).
 * The cold-chain breach scan uses a single batch query (findLatestForShipments)
 * rather than N individual queries — eliminating the previous N+1 problem.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ShipmentRepository        shipmentRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final FleetAssetRepository      fleetAssetRepository;
    private final TempReadingRepository     tempReadingRepository;
    private final ColdChainService          coldChainService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {

        // ── Aggregate counts — all single DB queries ───────────────────────────
        long totalShipments    = shipmentRepository.count();
        long activeDisruptions = disruptionEventRepository.countByStatus("ACTIVE");
        long coldChainCount    = shipmentRepository.countByColdChainTrue();
        long idleFleetCount    = fleetAssetRepository.countByStatus("IDLE");
        long inUseFleetCount   = fleetAssetRepository.countByStatus("IN_USE");
        long delayedCount      = shipmentRepository.countByStatusIgnoreCase("DELAYED");
        long inTransitCount    = shipmentRepository.countByStatusIgnoreCase("IN_TRANSIT");

        // ── Cold-chain breach scan — ONE batch query, not N queries ────────────
        List<Shipment> coldShipments = coldChainService.findColdChainShipments();
        long minorBreaches = 0, majorBreaches = 0;

        if (!coldShipments.isEmpty()) {
            // Fetch all latest readings for all cold-chain shipments in a single query
            List<TempReading> latestReadings = tempReadingRepository.findLatestForShipments(coldShipments);

            // Map shipment id → latest reading (only the most recent per shipment)
            Map<Long, TempReading> latestByShipmentId = latestReadings.stream()
                    .collect(Collectors.toMap(
                            r -> r.getShipment().getId(),
                            r -> r,
                            (a, b) -> a.getRecordedAt().isAfter(b.getRecordedAt()) ? a : b
                    ));

            for (Shipment s : coldShipments) {
                TempReading r = latestByShipmentId.get(s.getId());
                if (r != null) {
                    String status = coldChainService.breachStatus(r.getTemperatureCelsius());
                    if ("Minor Breach".equals(status))      minorBreaches++;
                    else if ("Major Breach".equals(status)) majorBreaches++;
                }
            }
        }

        // ── KPI attributes ─────────────────────────────────────────────────────
        model.addAttribute("totalShipments",    totalShipments);
        model.addAttribute("totalDisruptions",  activeDisruptions);
        model.addAttribute("coldChainCount",    coldChainCount);
        model.addAttribute("idleFleetCount",    idleFleetCount);
        model.addAttribute("inUseFleetCount",   inUseFleetCount);
        model.addAttribute("delayedCount",      delayedCount);
        model.addAttribute("inTransitCount",    inTransitCount);
        model.addAttribute("minorBreaches",     minorBreaches);
        model.addAttribute("majorBreaches",     majorBreaches);

        // ── Preview lists — use targeted queries, not findAll() ────────────────
        // 8 most recent shipments (newest first by id)
        model.addAttribute("recentShipments",
                shipmentRepository.findTopNOrderedById(PageRequest.of(0, 8)));

        // 4 most critical active disruptions (HIGH first)
        List<com.teamcatalyst.supplychain.model.DisruptionEvent> topDisruptions =
                disruptionEventRepository.findActiveOrderedBySeverity();
        model.addAttribute("recentDisruptions",
                topDisruptions.size() > 4 ? topDisruptions.subList(0, 4) : topDisruptions);

        // 6 fleet assets (any status for fleet overview)
        model.addAttribute("recentFleetAssets",
                fleetAssetRepository.findAll().stream().limit(6).toList());

        return "dashboard";
    }
}
