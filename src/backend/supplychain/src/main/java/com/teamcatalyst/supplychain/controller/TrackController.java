package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.service.ColdChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/track")
@RequiredArgsConstructor
public class TrackController {

    private final ShipmentRepository shipmentRepository;
    private final ColdChainService coldChainService;

    /** GET /track — show search form (empty state) */
    @GetMapping
    public String trackForm(@RequestParam(required = false) String trackingNumber,
                            Model model) {

        model.addAttribute("trackingNumber", trackingNumber);

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber.trim().toUpperCase());

            if (shipment == null) {
                model.addAttribute("notFound", true);
            } else {
                model.addAttribute("shipment", shipment);

                // Cold chain details + breach status (kept in a local variable for reuse)
                String breachStatus = null;
                if (shipment.isColdChain()) {
                    Optional<TempReading> reading = coldChainService.latestReading(shipment);
                    if (reading.isPresent()) {
                        TempReading r = reading.get();
                        breachStatus = coldChainService.breachStatus(r.getTemperatureCelsius());
                        model.addAttribute("tempReading", r);
                        model.addAttribute("breachStatus", breachStatus);
                    }
                }

                // Notify button shown for any breach or delayed shipment
                boolean showNotify = "Major Breach".equals(breachStatus)
                        || "Minor Breach".equals(breachStatus)
                        || "Delayed".equalsIgnoreCase(shipment.getStatus());
                model.addAttribute("showNotify", showNotify);
            }
        }

        return "track";
    }

    /**
     * POST /track/notify — notify the owner of a tracked shipment.
     * Redirects back to /track?trackingNumber=... with a flash message.
     */
    @PostMapping("/notify")
    public String notifyOwner(@RequestParam String trackingNumber,
                              RedirectAttributes redirectAttrs) {

        Shipment shipment = shipmentRepository.findByTrackingNumber(
                trackingNumber.trim().toUpperCase());

        if (shipment == null) {
            redirectAttrs.addFlashAttribute("notifyError",
                    "Shipment " + trackingNumber + " not found.");
            return "redirect:/track?trackingNumber=" + trackingNumber;
        }

        // Build issue description
        Optional<TempReading> reading = coldChainService.latestReading(shipment);
        String issue;
        if (reading.isPresent()) {
            String breach = coldChainService.breachStatus(reading.get().getTemperatureCelsius());
            issue = breach + " — latest reading: " + reading.get().getTemperatureCelsius() + " °C";
        } else {
            issue = "Delayed".equalsIgnoreCase(shipment.getStatus())
                    ? "Shipment delayed" : "Issue detected";
        }

        boolean sent = coldChainService.notifyOwner(shipment, issue);

        if (sent) {
            redirectAttrs.addFlashAttribute("notifySuccess",
                    "Shipment owner has been notified successfully.");
        } else {
            redirectAttrs.addFlashAttribute("notifyError",
                    "Could not notify owner — no contact details on record for "
                    + shipment.getTrackingNumber() + ".");
        }

        return "redirect:/track?trackingNumber=" + trackingNumber;
    }
}
