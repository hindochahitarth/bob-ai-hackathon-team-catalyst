package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.service.ColdChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/coldchain")
@RequiredArgsConstructor
public class ColdChainController {

    private final ColdChainService coldChainService;
    private final ShipmentRepository shipmentRepository;

    /** GET /coldchain — cold-chain shipments with latest temp + breach status */
    @GetMapping
    public String coldChain(Model model) {
        List<Shipment> shipments = coldChainService.findColdChainShipments();

        // Build a row list for the template
        List<ColdChainRow> rows = new ArrayList<>();
        for (Shipment s : shipments) {
            Optional<TempReading> reading = coldChainService.latestReading(s);
            if (reading.isPresent()) {
                TempReading r = reading.get();
                String status = coldChainService.breachStatus(r.getTemperatureCelsius());
                rows.add(new ColdChainRow(s, r, status));
            } else {
                // No reading yet — show as "No Data"
                rows.add(new ColdChainRow(s, null, "No Data"));
            }
        }

        long normalCount      = rows.stream().filter(r -> "Normal".equals(r.status())).count();
        long minorBreachCount = rows.stream().filter(r -> "Minor Breach".equals(r.status())).count();
        long majorBreachCount = rows.stream().filter(r -> "Major Breach".equals(r.status())).count();

        model.addAttribute("rows", rows);
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("normalCount", normalCount);
        model.addAttribute("minorBreachCount", minorBreachCount);
        model.addAttribute("majorBreachCount", majorBreachCount);

        return "coldchain";
    }

    /**
     * POST /coldchain/notify/{id} — notify the owner of a shipment about
     * a cold-chain breach or delay. Redirects back to /coldchain with a
     * flash message indicating success or failure.
     */
    @PostMapping("/notify/{id}")
    public String notifyOwner(@PathVariable Long id,
                              RedirectAttributes redirectAttrs) {

        Optional<Shipment> opt = shipmentRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttrs.addFlashAttribute("notifyError",
                    "Shipment #" + id + " not found.");
            return "redirect:/coldchain";
        }

        Shipment shipment = opt.get();

        // Determine the issue to include in the notification
        String issue = buildIssueDescription(shipment);

        boolean sent = coldChainService.notifyOwner(shipment, issue);

        if (sent) {
            redirectAttrs.addFlashAttribute("notifySuccess",
                    "Shipment owner has been notified successfully.");
        } else {
            redirectAttrs.addFlashAttribute("notifyError",
                    "Could not notify owner — no contact details on record for shipment "
                    + shipment.getTrackingNumber() + ".");
        }

        return "redirect:/coldchain";
    }

    /**
     * Builds a human-readable issue description from the shipment's
     * latest temperature reading and/or delayed status.
     */
    private String buildIssueDescription(Shipment shipment) {
        Optional<TempReading> reading = coldChainService.latestReading(shipment);
        String statusPart = "Delayed".equalsIgnoreCase(shipment.getStatus()) ? "Shipment delayed. " : "";
        if (reading.isPresent()) {
            String breachStatus = coldChainService.breachStatus(reading.get().getTemperatureCelsius());
            return statusPart + breachStatus + " — latest reading: "
                    + reading.get().getTemperatureCelsius() + " °C";
        }
        return statusPart.isBlank() ? "Breach or delay detected" : statusPart.trim();
    }

    /**
     * Immutable row DTO passed to the Thymeleaf template.
     * Using a Java record keeps this self-contained without a separate file.
     */
    public record ColdChainRow(Shipment shipment, TempReading reading, String status) {}
}
