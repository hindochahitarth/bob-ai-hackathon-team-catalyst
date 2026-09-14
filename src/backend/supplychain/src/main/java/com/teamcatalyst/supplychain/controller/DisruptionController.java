package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.service.DisruptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/disruptions")
@RequiredArgsConstructor
public class DisruptionController {

    private final DisruptionService disruptionService;

    /** GET /disruptions — list all disruption events */
    @GetMapping
    public String list(Model model) {
        List<DisruptionEvent> disruptions = disruptionService.findAll();
        model.addAttribute("disruptions", disruptions);
        model.addAttribute("totalCount", disruptions.size());
        model.addAttribute("highCount",
                disruptions.stream().filter(d -> "HIGH".equalsIgnoreCase(d.getSeverity())).count());
        return "disruptions";
    }

    /** GET /disruptions/{id}/impact — detail + affected shipments */
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

        // Build a map of shipment → suggested action for the template
        Map<Shipment, String> shipmentActions = new LinkedHashMap<>();
        for (Shipment s : affected) {
            shipmentActions.put(s, disruptionService.suggestAction(event, s));
        }

        model.addAttribute("event", event);
        model.addAttribute("shipmentActions", shipmentActions);
        model.addAttribute("affectedCount", affected.size());

        return "disruption-impact";
    }
}
