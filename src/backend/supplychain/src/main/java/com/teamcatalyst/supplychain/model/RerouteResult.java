package com.teamcatalyst.supplychain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Encapsulates the output of Dijkstra's graph-based route optimization algorithm
 * when calculating alternate bypass routes around active disruption events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerouteResult {

    /** Whether an alternate route was successfully computed. */
    private boolean success;

    /** Sequence of waypoint nodes in original route. */
    private List<String> originalPath;

    /** Sequence of waypoint nodes in Dijkstra-optimized alternate route. */
    private List<String> alternatePath;

    /** Original corridor distance in km. */
    private double originalDistanceKm;

    /** Optimized alternate corridor distance in km. */
    private double alternateDistanceKm;

    /** Difference in distance (alternate - original) in km. */
    private double deltaKm;

    /** Estimated transit delay in hours based on assumed average commercial speed (50 km/h). */
    private double estimatedDelayHours;

    /** Name of the recommended carrier selected via multi-factor scoring. */
    private String recommendedCarrier;

    /** Carrier evaluation score (0-100 scale). */
    private double carrierScore;

    /** Risk classification of the bypass route (LOW, MEDIUM, HIGH). */
    private String riskLevel;

    /** Human-readable operational explanation of the reroute. */
    private String summary;

    /** Key bypass highway/segment utilized in the alternate path. */
    private String bypassSegment;

    /** Returns formatted original path string (e.g., "A → B → C"). */
    public String getOriginalPathFormatted() {
        return originalPath == null || originalPath.isEmpty() ? "—" : String.join(" → ", originalPath);
    }

    /** Returns formatted alternate path string (e.g., "A → B → C"). */
    public String getAlternatePathFormatted() {
        return alternatePath == null || alternatePath.isEmpty() ? "No feasible route" : String.join(" → ", alternatePath);
    }
}
