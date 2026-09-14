package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.service.ColdChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class TrackingController {

    private final ShipmentRepository shipmentRepo;
    private final ColdChainService coldChainService;

    /** GET /track — empty search page */
    @GetMapping("/track")
    public String trackPage(@RequestParam(required = false) String trackingNumber,
                            Model model) {

        model.addAttribute("trackingNumber", trackingNumber);
        model.addAttribute("searched", false);

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            model.addAttribute("searched", true);

            Shipment shipment = shipmentRepo.findByTrackingNumber(trackingNumber.trim());

            if (shipment != null) {
                model.addAttribute("shipment", shipment);
                model.addAttribute("found", true);

                // Latest temperature for cold-chain shipments
                if (shipment.isColdChain()) {
                    Optional<TempReading> latest = coldChainService.latestReading(shipment);
                    latest.ifPresent(r -> {
                        model.addAttribute("latestTemp", r);
                        model.addAttribute("breachStatus",
                                coldChainService.breachStatus(r.getTemperatureCelsius()));
                    });
                }
            } else {
                model.addAttribute("found", false);
            }
        }

        return "track";
    }

    /** GET /shipments & GET /track/shipments — full shipments list page */
    @GetMapping({"/shipments", "/track/shipments"})
    public String allShipments(Model model) {
        model.addAttribute("shipments", shipmentRepo.findAll());
        return "shipments";
    }
}
