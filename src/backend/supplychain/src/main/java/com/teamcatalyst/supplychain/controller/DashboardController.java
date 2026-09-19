package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.FleetAssetRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.service.ColdChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ShipmentRepository shipmentRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final FleetAssetRepository fleetAssetRepository;
    private final ColdChainService coldChainService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        long totalShipments    = shipmentRepository.count();
        long activeDisruptions = disruptionEventRepository.findAll().stream()
                                     .filter(d -> "ACTIVE".equals(d.getStatus())).count();
        long coldChainCount    = shipmentRepository.countByColdChainTrue();
        long idleFleetCount    = fleetAssetRepository.countByStatus("IDLE");
        long inUseFleetCount   = fleetAssetRepository.countByStatus("IN_USE");
        long delayedCount      = shipmentRepository.findAll().stream()
                                     .filter(s -> "DELAYED".equalsIgnoreCase(s.getStatus())).count();

        // Cold chain breach counts for the live indicator
        long minorBreaches = 0, majorBreaches = 0;
        for (Shipment s : coldChainService.findColdChainShipments()) {
            Optional<TempReading> r = coldChainService.latestReading(s);
            if (r.isPresent()) {
                String status = coldChainService.breachStatus(r.get().getTemperatureCelsius());
                if ("Minor Breach".equals(status)) minorBreaches++;
                else if ("Major Breach".equals(status)) majorBreaches++;
            }
        }

        model.addAttribute("totalShipments",    totalShipments);
        model.addAttribute("totalDisruptions",  activeDisruptions);
        model.addAttribute("coldChainCount",    coldChainCount);
        model.addAttribute("idleFleetCount",    idleFleetCount);
        model.addAttribute("inUseFleetCount",   inUseFleetCount);
        model.addAttribute("delayedCount",      delayedCount);
        model.addAttribute("minorBreaches",     minorBreaches);
        model.addAttribute("majorBreaches",     majorBreaches);

        model.addAttribute("recentShipments",   shipmentRepository.findAll().stream().limit(8).toList());
        model.addAttribute("recentDisruptions", disruptionEventRepository.findAll().stream()
                                                    .filter(d -> "ACTIVE".equals(d.getStatus()))
                                                    .limit(4).toList());
        model.addAttribute("recentFleetAssets", fleetAssetRepository.findAll().stream().limit(6).toList());

        return "dashboard";
    }
}
