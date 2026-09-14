package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import com.teamcatalyst.supplychain.model.OpsStats;
import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.FleetAssetRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.service.ColdChainService;
import com.teamcatalyst.supplychain.service.DisruptionService;
import com.teamcatalyst.supplychain.service.WatsonxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OpsController - AI Operations Brief page.
 * Collects live supply-chain statistics and delegates to WatsonxService.
 */
@Controller
@RequestMapping("/ops-brief")
@RequiredArgsConstructor
public class OpsController {

    private final WatsonxService watsonxService;
    private final ShipmentRepository shipmentRepo;
    private final DisruptionEventRepository disruptionRepo;
    private final FleetAssetRepository fleetRepo;
    private final ColdChainService coldChainService;
    private final DisruptionService disruptionService;

    @GetMapping
    public String opsBrief(Model model) {
        model.addAttribute("briefText",   null);
        model.addAttribute("generated",   false);
        model.addAttribute("aiGenerated", false);
        return "ops-brief";
    }

    @PostMapping
    public String generateBrief(RedirectAttributes redirectAttrs) {
        OpsStats stats = collectStats();
        WatsonxService.BriefResult result = watsonxService.generateOpsBrief(stats);

        redirectAttrs.addFlashAttribute("briefText",   result.text());
        redirectAttrs.addFlashAttribute("generated",   true);
        redirectAttrs.addFlashAttribute("aiGenerated", result.aiGenerated());
        redirectAttrs.addFlashAttribute("aiSource",    result.source() != null ? result.source() : "System Generated");
        redirectAttrs.addFlashAttribute("stats",       stats);
        return "redirect:/ops-brief";
    }

    private OpsStats collectStats() {
        List<Shipment> allShipments = shipmentRepo.findAll();
        List<DisruptionEvent> disruptions = disruptionRepo.findAll();

        // Disruptions + affected shipments (use Set to avoid double-counting)
        java.util.Set<Long> disruptedIds = new java.util.HashSet<>();
        List<String> majorSummaries = new ArrayList<>();

        for (DisruptionEvent event : disruptions) {
            List<Shipment> affected = disruptionService.findAffectedShipments(event);
            affected.forEach(s -> disruptedIds.add(s.getId()));

            String sev = event.getSeverity();
            if ("HIGH".equalsIgnoreCase(sev) || "CRITICAL".equalsIgnoreCase(sev)) {
                majorSummaries.add(event.getType() + " on " + event.getAffectedSegment()
                        + " [" + sev + "] - " + event.getDescription());
            }
        }
        int disruptedShipments = disruptedIds.size();

        int idleFleet = (int) fleetRepo.countByStatus("IDLE");

        List<Shipment> coldChainShipments = coldChainService.findColdChainShipments();
        int coldAlerts = 0, criticalAlerts = 0;
        for (Shipment s : coldChainShipments) {
            Optional<TempReading> latest = coldChainService.latestReading(s);
            if (latest.isPresent()) {
                String status = coldChainService.breachStatus(latest.get().getTemperatureCelsius());
                if ("Minor Breach".equals(status)) coldAlerts++;
                else if ("Major Breach".equals(status)) { coldAlerts++; criticalAlerts++; }
            }
        }

        return OpsStats.builder()
                .activeShipments(allShipments.size())
                .disruptedShipments(disruptedShipments)
                .activeDisruptions(disruptions.size())
                .idleFleetAssets(idleFleet)
                .coldChainAlerts(coldAlerts)
                .criticalColdChainAlerts(criticalAlerts)
                .majorDisruptionSummaries(majorSummaries)
                .build();
    }
}