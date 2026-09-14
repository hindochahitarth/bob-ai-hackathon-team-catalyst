package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.service.ColdChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/coldchain")
@RequiredArgsConstructor
public class ColdChainController {

    private final ColdChainService coldChainService;

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
     * Immutable row DTO passed to the Thymeleaf template.
     * Using a Java record keeps this self-contained without a separate file.
     */
    public record ColdChainRow(Shipment shipment, TempReading reading, String status) {}
}
