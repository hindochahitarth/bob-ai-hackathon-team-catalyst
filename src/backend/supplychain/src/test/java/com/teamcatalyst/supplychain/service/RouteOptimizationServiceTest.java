package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.RerouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RouteOptimizationService — Dijkstra Algorithm & Multi-Criteria Carrier Tests")
class RouteOptimizationServiceTest {

    private RouteOptimizationService routeOptimizationService;

    @BeforeEach
    void setUp() {
        routeOptimizationService = new RouteOptimizationService();
    }

    @Test
    @DisplayName("Test 1: Normal route calculation without disruption")
    void testNormalRouteWithoutDisruptions() {
        RerouteResult result = routeOptimizationService.findOptimalRoute(
                "Mumbai",
                "Delhi",
                Collections.emptySet(),
                Collections.emptySet(),
                "Electronics"
        );

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccess(), "Dijkstra should find a route on intact network");
        assertEquals(1420.0, result.getOriginalDistanceKm(), 0.1, "Original distance should match standard corridor");
        assertEquals(1420.0, result.getAlternateDistanceKm(), 0.1, "Alternate should match original when intact");
        assertEquals(0.0, result.getDeltaKm(), 0.01, "Delta should be 0 when no disruption active");
        assertTrue(result.getAlternatePath().contains("SURAT_HUB"));
        assertTrue(result.getAlternatePath().contains("JAIPUR_CORRIDOR"));
        assertNotNull(result.getRecommendedCarrier(), "An eligible carrier should be recommended");
    }

    @Test
    @DisplayName("Test 2: Blocked corridor triggers Dijkstra bypass and positive delta")
    void testBlockedCorridorTriggersBypassAndDelta() {
        // Disruption blocks NH48 corridor
        RerouteResult result = routeOptimizationService.findOptimalRoute(
                "Mumbai",
                "Delhi",
                Set.of("NH48"),
                Collections.emptySet(),
                "Pharmaceuticals"
        );

        assertNotNull(result);
        assertTrue(result.isSuccess(), "Dijkstra should find an alternate bypass path avoiding NH48");
        assertTrue(result.getAlternateDistanceKm() > result.getOriginalDistanceKm(),
                "Alternate distance should be longer than normal highway corridor");
        assertTrue(result.getDeltaKm() > 0.0, "Delta distance must be strictly positive");
        assertFalse(result.getAlternatePath().contains("SURAT_HUB"),
                "Bypass path should avoid SURAT_HUB on blocked NH48 corridor");
        assertTrue(result.getAlternatePath().contains("NASHIK_HUB"),
                "Bypass path should route through Nashik bypass junction");
        assertEquals(result.getDeltaKm(),
                Math.round((result.getAlternateDistanceKm() - result.getOriginalDistanceKm()) * 10.0) / 10.0,
                0.1, "Delta km must exactly match (alternate - original)");
        assertTrue(result.getEstimatedDelayHours() > 0.0, "Estimated delay must be calculated");
    }

    @Test
    @DisplayName("Test 3: Multiple blocked corridors finds remaining viable alternative")
    void testMultipleBlockedCorridorsFindsViableAlternative() {
        // Block both NH48 and NH52_CORRIDOR
        RerouteResult result = routeOptimizationService.findOptimalRoute(
                "Mumbai",
                "Delhi",
                Set.of("NH48", "NH52_CORRIDOR"),
                Collections.emptySet(),
                "Textiles"
        );

        assertNotNull(result);
        assertTrue(result.isSuccess(), "Dijkstra should navigate remaining network via Pune/Nagpur/Gwalior");
        assertTrue(result.getAlternatePath().contains("NAGPUR_CROSSING"));
    }

    @Test
    @DisplayName("Test 4: No available route returns clean failure")
    void testNoAvailableRouteReturnsFailure() {
        // Block all outbound segments from Mumbai
        RerouteResult result = routeOptimizationService.findOptimalRoute(
                "Mumbai",
                "Delhi",
                Set.of("NH48", "NH160_BYPASS", "NH3_BYPASS", "NH53"),
                Collections.emptySet(),
                "General"
        );

        assertNotNull(result);
        assertFalse(result.isSuccess(), "Should fail gracefully when all outbound corridors are severed");
        assertTrue(result.getAlternatePath().isEmpty(), "Alternate path should be empty on failure");
        assertNotNull(result.getSummary());
    }

    @Test
    @DisplayName("Test 5: Carrier selection excludes unavailable carriers and prioritizes reliability & suitability")
    void testCarrierSelectionExcludesUnavailableAndScoresReliability() {
        // Gati-KWE is initialized as available = false in carrier pool
        RerouteResult result = routeOptimizationService.findOptimalRoute(
                "Mumbai",
                "Delhi",
                Set.of("NH48"),
                Collections.emptySet(),
                "Vaccines"
        );

        assertTrue(result.isSuccess());
        assertNotEquals("Gati-KWE", result.getRecommendedCarrier(),
                "Unavailable carrier must be strictly excluded from assignment");

        // BlueDart Cargo specializes in Vaccines and has 96% reliability score
        assertEquals("BlueDart Cargo", result.getRecommendedCarrier(),
                "High reliability specialized cold-chain carrier should be selected for Vaccines");
        assertTrue(result.getCarrierScore() >= 80.0, "Carrier score should reflect high qualification");
    }

    @Test
    @DisplayName("Test 6: Delta calculation and speed-delay formula accuracy")
    void testDeltaCalculationAccuracy() {
        RerouteResult result = routeOptimizationService.findOptimalRoute(
                "Mumbai",
                "Delhi",
                Set.of("NH48"),
                Collections.emptySet(),
                "Automotive Parts"
        );

        assertTrue(result.isSuccess());
        double expectedDelta = Math.round((result.getAlternateDistanceKm() - result.getOriginalDistanceKm()) * 10.0) / 10.0;
        assertEquals(expectedDelta, result.getDeltaKm(), 0.001);

        double expectedDelay = Math.round((result.getDeltaKm() / 50.0) * 10.0) / 10.0;
        assertEquals(expectedDelay, result.getEstimatedDelayHours(), 0.001);
    }
}
