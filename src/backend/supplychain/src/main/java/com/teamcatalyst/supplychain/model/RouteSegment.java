package com.teamcatalyst.supplychain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RouteSegment — a live, persisted edge in the Indian freight network graph.
 *
 * Every corridor (e.g. MUMBAI_PORT → SURAT_HUB via NH48) is stored as a
 * database row so the graph is no longer hard-coded in Java source. The
 * simulation engine updates congestionScore, blocked, and incidentType in
 * real-time so Dijkstra always picks up the current network state.
 *
 * congestionScore : 0.0 (free-flow) – 1.0 (gridlock), used as an edge-weight
 *                   multiplier so congested roads are less attractive.
 * blocked          : hard block — Dijkstra skips this edge entirely.
 * incidentType     : CLEAR, CONGESTION, ACCIDENT, WEATHER, CLOSURE, ROADWORKS
 * speedKmh         : current average speed on this segment (free-flow default).
 * lastUpdated      : timestamp of the last simulation tick that touched this row.
 */
@Entity
@Table(name = "route_segments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source node ID — matches the graph constants, e.g. "MUMBAI_PORT". */
    @Column(nullable = false)
    private String fromNode;

    /** Destination node ID. */
    @Column(nullable = false)
    private String toNode;

    /** National/State Highway identifier, e.g. "NH48", "SH10_BYPASS". */
    @Column(nullable = false)
    private String segmentId;

    /** Human-readable corridor name shown in the UI. */
    private String corridorName;

    /** Base distance in km (does not change). */
    private double distanceKm;

    /** Current incident type: CLEAR | CONGESTION | ACCIDENT | WEATHER | CLOSURE | ROADWORKS */
    @Column(nullable = false)
    private String incidentType = "CLEAR";

    /**
     * Congestion score 0.0–1.0.
     * Dijkstra effective weight = distanceKm * (1 + congestionScore * 2)
     * so a fully congested road is treated as 3× its base distance.
     */
    @Column(nullable = false)
    private double congestionScore = 0.0;

    /**
     * Hard block flag. When true, the edge is removed from the Dijkstra graph
     * entirely (just like a disruption on that segment).
     */
    @Column(nullable = false)
    private boolean blocked = false;

    /** Current average speed on this corridor (km/h). */
    private double speedKmh = 60.0;

    /** Estimated delay minutes added by current conditions. */
    private int additionalDelayMinutes = 0;

    /** Free-text description of the current live condition. */
    private String liveConditionDesc;

    /** Last time this row was updated by the simulation engine. */
    private LocalDateTime lastUpdated;

    /** Returns the effective Dijkstra weight accounting for congestion. */
    public double effectiveWeight() {
        if (blocked) return Double.MAX_VALUE;
        return distanceKm * (1.0 + congestionScore * 2.0);
    }
}
