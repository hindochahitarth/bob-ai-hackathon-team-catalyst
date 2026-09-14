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

    /** GET /fleet — redirect to idle list */
    @GetMapping
    public String fleet() {
        return "redirect:/fleet/idle";
    }

    /** GET /fleet/idle — list all assets with status IDLE */
    @GetMapping("/idle")
    public String idleFleet(Model model) {
        List<FleetAsset> idleAssets = fleetAssetRepository.findByStatus("IDLE");
        model.addAttribute("idleAssets", idleAssets);
        model.addAttribute("idleCount", idleAssets.size());
        return "fleet-idle";
    }
}
