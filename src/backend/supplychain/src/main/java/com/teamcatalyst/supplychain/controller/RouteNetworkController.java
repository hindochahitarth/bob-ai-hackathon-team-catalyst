package com.teamcatalyst.supplychain.controller;

import com.teamcatalyst.supplychain.model.RouteSegment;
import com.teamcatalyst.supplychain.repository.RouteSegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RouteNetworkController
 *
 * Serves the live Network Map page and the REST API endpoints that power it.
 * REST endpoints are consumed by the network-map.html SSE client.
 */
@Controller
@RequiredArgsConstructor
public class RouteNetworkController {

    private final RouteSegmentRepository segmentRepo;

    // ── Thymeleaf page ────────────────────────────────────────────────────────

    @GetMapping("/network")
    public String networkMap(Model model) {
        List<RouteSegment> all = segmentRepo.findAll();

        long totalSegments  = all.size();
        long blockedCount   = all.stream().filter(RouteSegment::isBlocked).count();
        long incidentCount  = all.stream().filter(s -> !"CLEAR".equals(s.getIncidentType())).count();
        long highCongestion = all.stream().filter(s -> s.getCongestionScore() >= 0.6).count();
        double avgCongestion = all.stream().mapToDouble(RouteSegment::getCongestionScore).average().orElse(0.0);

        // Group segments by highway/segmentId for corridor summary
        Map<String, List<RouteSegment>> byHighway = all.stream()
                .collect(Collectors.groupingBy(RouteSegment::getSegmentId));

        // Top-5 most congested segments
        List<RouteSegment> topCongested = all.stream()
                .sorted(Comparator.comparingDouble(RouteSegment::getCongestionScore).reversed())
                .limit(5)
                .collect(Collectors.toList());

        model.addAttribute("segments",       all);
        model.addAttribute("totalSegments",  totalSegments);
        model.addAttribute("blockedCount",   blockedCount);
        model.addAttribute("incidentCount",  incidentCount);
        model.addAttribute("highCongestion", highCongestion);
        model.addAttribute("avgCongestion",  Math.round(avgCongestion * 100.0));
        model.addAttribute("byHighway",      byHighway);
        model.addAttribute("topCongested",   topCongested);

        return "network-map";
    }

    // ── REST API — consumed by live-update JS ─────────────────────────────────

    /** All segments — full list with live state. */
    @GetMapping(value = "/api/v1/network/segments", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<SegmentDto> getAllSegments() {
        return segmentRepo.findAllOrderedByCongestion().stream()
                .map(SegmentDto::from)
                .collect(Collectors.toList());
    }

    /** Single segment by DB id. */
    @GetMapping(value = "/api/v1/network/segments/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SegmentDto getSegment(@PathVariable Long id) {
        return segmentRepo.findById(id).map(SegmentDto::from).orElse(null);
    }

    /** High-congestion segments only (score >= 0.5). */
    @GetMapping(value = "/api/v1/network/segments/congested", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<SegmentDto> getCongestedSegments() {
        return segmentRepo.findHighCongestion(0.5).stream()
                .map(SegmentDto::from)
                .collect(Collectors.toList());
    }

    /** Currently blocked segments. */
    @GetMapping(value = "/api/v1/network/segments/blocked", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<SegmentDto> getBlockedSegments() {
        return segmentRepo.findByBlocked(true).stream()
                .map(SegmentDto::from)
                .collect(Collectors.toList());
    }

    /** Network health summary — used by dashboard KPI card. */
    @GetMapping(value = "/api/v1/network/health", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public NetworkHealthDto getNetworkHealth() {
        List<RouteSegment> all = segmentRepo.findAll();
        long blocked   = all.stream().filter(RouteSegment::isBlocked).count();
        long incidents = all.stream().filter(s -> !"CLEAR".equals(s.getIncidentType())).count();
        double avgCong = all.stream().mapToDouble(RouteSegment::getCongestionScore).average().orElse(0.0);
        int healthScore = (int) Math.max(0, Math.min(100,
                100 - (blocked * 20) - (incidents * 5) - (avgCong * 30)));
        return new NetworkHealthDto(all.size(), (int) blocked, (int) incidents,
                Math.round(avgCong * 100.0), healthScore);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record SegmentDto(
            Long   id,
            String fromNode,
            String toNode,
            String segmentId,
            String corridorName,
            double distanceKm,
            String incidentType,
            double congestionScore,
            int    congestionPct,
            boolean blocked,
            double speedKmh,
            int    additionalDelayMinutes,
            String liveConditionDesc,
            String lastUpdated,
            String statusClass
    ) {
        public static SegmentDto from(RouteSegment s) {
            String cls = s.isBlocked() ? "blocked"
                       : s.getCongestionScore() >= 0.7 ? "high"
                       : s.getCongestionScore() >= 0.4 ? "medium"
                       : "clear";
            return new SegmentDto(
                    s.getId(), s.getFromNode(), s.getToNode(),
                    s.getSegmentId(), s.getCorridorName(),
                    s.getDistanceKm(), s.getIncidentType(),
                    s.getCongestionScore(),
                    (int) Math.round(s.getCongestionScore() * 100),
                    s.isBlocked(), s.getSpeedKmh(),
                    s.getAdditionalDelayMinutes(),
                    s.getLiveConditionDesc(),
                    s.getLastUpdated() != null ? s.getLastUpdated().toString() : "",
                    cls
            );
        }
    }

    public record NetworkHealthDto(
            int totalSegments, int blocked, int incidents,
            long avgCongestionPct, int healthScore) {}
}
