package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.FleetAsset;
import com.teamcatalyst.supplychain.repository.FleetAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/fleet")
@RequiredArgsConstructor
public class FleetController {

    private final FleetAssetRepository fleetAssetRepository;

    @GetMapping
    public String fleet() {
        return "redirect:/fleet/idle";
    }

    @GetMapping("/idle")
    public String idleFleet(Model model) {
        List<FleetAsset> idleAssets = fleetAssetRepository.findByStatus("IDLE");
        long idleCount   = fleetAssetRepository.countByStatus("IDLE");
        long inUseCount  = fleetAssetRepository.countByStatus("IN_USE");
        long totalCount  = fleetAssetRepository.count();

        model.addAttribute("idleAssets", idleAssets);
        model.addAttribute("idleCount",  idleCount);
        model.addAttribute("inUseCount", inUseCount);
        model.addAttribute("totalCount", totalCount);
        return "fleet-idle";
    }
}
