package com.teamcatalyst.supplychain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Aggregated operational statistics for the watsonx.ai prompt and fallback Ops Brief.
 */
@Data
@Builder
public class OpsStats {
    private int activeShipments;
    private int disruptedShipments;
    private int activeDisruptions;
    private int idleFleetAssets;
    private int coldChainAlerts;
    private int criticalColdChainAlerts;
    private List<String> majorDisruptionSummaries;
}