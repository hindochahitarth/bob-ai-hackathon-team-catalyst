package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.FleetAssetRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ShipmentRepository shipmentRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final FleetAssetRepository fleetAssetRepository;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        long totalShipments = shipmentRepository.count();
        long totalDisruptions = disruptionEventRepository.count();
        long coldChainCount = shipmentRepository.countByColdChainTrue();
        long idleFleetCount = fleetAssetRepository.countByStatus("IDLE");

        model.addAttribute("totalShipments", totalShipments);
        model.addAttribute("totalDisruptions", totalDisruptions);
        model.addAttribute("coldChainCount", coldChainCount);
        model.addAttribute("idleFleetCount", idleFleetCount);

        // Populate lists for tables and cards
        model.addAttribute("recentShipments", shipmentRepository.findAll().stream().limit(5).toList());
        model.addAttribute("recentDisruptions", disruptionEventRepository.findAll().stream().limit(3).toList());
        model.addAttribute("recentFleetAssets", fleetAssetRepository.findAll().stream().limit(4).toList());

        return "dashboard";
    }
}
