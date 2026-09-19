package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import com.teamcatalyst.supplychain.model.RerouteResult;
import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.service.DisruptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/disruptions")
@RequiredArgsConstructor
@Slf4j
public class DisruptionController {

    private final DisruptionService disruptionService;
    private final ShipmentRepository shipmentRepository;
    private final DisruptionEventRepository disruptionRepo;

    /** GET /disruptions — list all disruption events */
    @GetMapping
    public String list(Model model) {
        List<DisruptionEvent> disruptions = disruptionService.findAll();
        long activeCount = disruptions.stream().filter(d -> "ACTIVE".equals(d.getStatus())).count();
        long resolvedCount = disruptions.stream().filter(d -> "RESOLVED".equals(d.getStatus())).count();
        model.addAttribute("disruptions", disruptions);
        model.addAttribute("totalCount", disruptions.size());
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("resolvedCount", resolvedCount);
        model.addAttribute("highCount",
                disruptions.stream().filter(d -> "HIGH".equalsIgnoreCase(d.getSeverity())
                        && "ACTIVE".equals(d.getStatus())).count());
        return "disruptions";
    }

    /** POST /disruptions/{id}/resolve — mark a disruption as RESOLVED */
    @PostMapping("/{id}/resolve")
    public String resolveDisruption(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        Optional<DisruptionEvent> opt = disruptionService.findById(id);
        if (opt.isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "Disruption #" + id + " not found.");
            return "redirect:/disruptions";
        }
        DisruptionEvent event = opt.get();
        event.setStatus("RESOLVED");
        event.setResolvedAt(java.time.LocalDateTime.now());
        disruptionRepo.save(event);
        redirectAttrs.addFlashAttribute("successMessage",
            "✅ Disruption #" + id + " marked as RESOLVED.");
        return "redirect:/disruptions";
    }

    /** GET /disruptions/create — redirect to disruptions page and open modal via JS */
    @GetMapping("/create")
    public String showCreateForm(RedirectAttributes redirectAttrs) {
        redirectAttrs.addFlashAttribute("openCreateModal", true);
        return "redirect:/disruptions";
    }

    /** POST /disruptions/create — create a new disruption event */
    @PostMapping("/create")
    public String createDisruption(
            @RequestParam String type,
            @RequestParam String affectedSegment,
            @RequestParam String description,
            @RequestParam String severity,
            RedirectAttributes redirectAttrs) {
        DisruptionEvent event = new DisruptionEvent(
            null, type, affectedSegment, description, severity,
            "ACTIVE", java.time.LocalDateTime.now(), null
        );
        disruptionRepo.save(event);
        redirectAttrs.addFlashAttribute("successMessage",
            "⚡ New disruption event created: " + type + " on " + affectedSegment);
        return "redirect:/disruptions";
    }

    /** GET /disruptions/{id}/impact — detail + affected shipments with Dijkstra reroute optimization */
    @GetMapping("/{id}/impact")
    public String impact(@PathVariable Long id,
                         Model model,
                         RedirectAttributes redirectAttrs) {

        Optional<DisruptionEvent> opt = disruptionService.findById(id);
        if (opt.isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "Disruption #" + id + " not found.");
            return "redirect:/disruptions";
        }

        DisruptionEvent event = opt.get();
        List<Shipment> affected = disruptionService.findAffectedShipments(event);

        // Compute Dijkstra-optimized alternate paths for each affected shipment
        Map<Shipment, RerouteResult> rerouteResults = new LinkedHashMap<>();
        for (Shipment s : affected) {
            RerouteResult result = disruptionService.suggestReroute(event, s);
            rerouteResults.put(s, result);
        }

        // Feature sample reroute for the visual comparison card
        RerouteResult sampleReroute = rerouteResults.values().stream()
                .filter(RerouteResult::isSuccess)
                .findFirst()
                .orElse(null);

        model.addAttribute("event", event);
        model.addAttribute("rerouteResults", rerouteResults);
        model.addAttribute("sampleReroute", sampleReroute);
        model.addAttribute("affectedCount", affected.size());

        return "disruption-impact";
    }

    /**
     * POST /disruptions/{id}/reroute/{shipmentId}
     * Dispatcher approves Dijkstra-recommended alternate route for a shipment.
     */
    @PostMapping("/{id}/reroute/{shipmentId}")
    public String approveReroute(@PathVariable Long id,
                                 @PathVariable Long shipmentId,
                                 RedirectAttributes redirectAttrs) {

        Optional<DisruptionEvent> eventOpt = disruptionService.findById(id);
        Optional<Shipment> shipmentOpt = shipmentRepository.findById(shipmentId);

        if (eventOpt.isEmpty() || shipmentOpt.isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "Disruption or Shipment not found.");
            return "redirect:/disruptions/" + id + "/impact";
        }

        DisruptionEvent event = eventOpt.get();
        Shipment shipment = shipmentOpt.get();

        RerouteResult reroute = disruptionService.suggestReroute(event, shipment);
        if (!reroute.isSuccess()) {
            redirectAttrs.addFlashAttribute("error", "Cannot approve reroute: " + reroute.getSummary());
            return "redirect:/disruptions/" + id + "/impact";
        }

        // Apply reroute update
        shipment.setStatus("IN_TRANSIT (REROUTED)");
        if (reroute.getBypassSegment() != null) {
            shipment.setCurrentLocation("Bypassing via " + reroute.getBypassSegment());
        }

        shipmentRepository.save(shipment);

        log.info("Dispatcher approved Dijkstra reroute for shipment {} via carrier {}",
                shipment.getTrackingNumber(), reroute.getRecommendedCarrier());

        redirectAttrs.addFlashAttribute("successMessage", String.format(
                "✅ Reroute approved for %s! Assigned to %s via %s (+%.0f km).",
                shipment.getTrackingNumber(), reroute.getRecommendedCarrier(),
                reroute.getBypassSegment(), reroute.getDeltaKm()
        ));

        return "redirect:/disruptions/" + id + "/impact";
    }
}
