package com.teamcatalyst.supplychain.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ops-brief")
public class OpsController {

    /** GET /ops-brief — render the AI Ops Brief page */
    @GetMapping
    public String opsBrief(Model model) {
        // Placeholder brief — will be replaced with IBM Bob API response later
        model.addAttribute("briefText", null);
        model.addAttribute("generated", false);
        return "ops-brief";
    }

    /**
     * POST /ops-brief — trigger brief generation.
     * Currently returns a static placeholder; wire to IBM Bob API here later.
     */
    @PostMapping
    public String generateBrief(RedirectAttributes redirectAttrs) {
        String placeholderBrief =
                "📊 Operations Summary — " + java.time.LocalDate.now() + "\n\n" +
                "▸ Active Shipments: 142 in transit across 18 corridors.\n" +
                "▸ Disruptions: 5 active events — 2 HIGH severity (JNPT congestion, NH-48 closure). " +
                  "Recommend immediate rerouting for 12 affected loads.\n" +
                "▸ Cold Chain: 98% compliance. 1 minor breach detected on TRK-00421 (6.2°C → within range). " +
                  "No critical breaches.\n" +
                "▸ Idle Fleet: 17 assets idle — 9 heavy trucks in Delhi hub, 4 vans in Mumbai. " +
                  "Recommend dispatching 5 units to cover disrupted routes.\n" +
                "▸ Recommended Actions:\n" +
                "   1. Reroute loads on NH-48 via SH-8 (est. +4h delay).\n" +
                "   2. Clear 3 idle trucks from Delhi for Bangalore corridor.\n" +
                "   3. Schedule maintenance window for TRK-C22 (currently in maintenance at Chennai).\n\n" +
                "ℹ️ This brief is AI-generated. Verify critical decisions with operations team.";

        redirectAttrs.addFlashAttribute("briefText", placeholderBrief);
        redirectAttrs.addFlashAttribute("generated", true);
        return "redirect:/ops-brief";
    }
}
