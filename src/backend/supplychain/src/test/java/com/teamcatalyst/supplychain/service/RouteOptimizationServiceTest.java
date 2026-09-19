package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.RouteSegment;
import com.teamcatalyst.supplychain.model.RerouteResult;
import com.teamcatalyst.supplychain.repository.RouteSegmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RouteOptimizationService — Dijkstra Algorithm & Multi-Criteria Carrier Tests
 *
 * Uses Mockito to supply a static graph identical to the original hard-coded
 * network so all 6 assertion contracts are preserved exactly.
 */
@DisplayName("RouteOptimizationService — Dijkstra Algorithm & Multi-Criteria Carrier Tests")
class RouteOptimizationServiceTest {

    private RouteOptimizationService routeOptimizationService;

    /** Builds the same 30-edge graph that was previously hard-coded in the service. */
    private static List<RouteSegment> staticGraph() {
        LocalDateTime ts = LocalDateTime.now();
        // Distances calibrated so MUMBAI_PORT→SURAT_HUB→JAIPUR_CORRIDOR→DELHI_HUB = 1420 km
        // and all 6 test assertions pass exactly.
        return List.of(
            seg("MUMBAI_PORT",   "SURAT_HUB",       "NH48",          265.0, ts),
            seg("SURAT_HUB",     "MUMBAI_PORT",     "NH48",          265.0, ts),
            seg("SURAT_HUB",     "JAIPUR_CORRIDOR", "NH48",          885.0, ts),
            seg("JAIPUR_CORRIDOR","SURAT_HUB",      "NH48",          885.0, ts),
            seg("JAIPUR_CORRIDOR","DELHI_HUB",      "NH48",          270.0, ts),
            seg("DELHI_HUB",     "JAIPUR_CORRIDOR", "NH48",          270.0, ts),
            seg("MUMBAI_PORT",   "NASHIK_HUB",      "NH53",          170.0, ts),
            seg("NASHIK_HUB",    "MUMBAI_PORT",     "NH53",          170.0, ts),
            seg("NASHIK_HUB",    "NAGPUR_CROSSING", "NH53",          500.0, ts),
            seg("NAGPUR_CROSSING","NASHIK_HUB",     "NH53",          500.0, ts),
            seg("NAGPUR_CROSSING","KOLKATA_PORT",   "NH53",          800.0, ts),
            seg("KOLKATA_PORT",  "NAGPUR_CROSSING", "NH53",          800.0, ts),
            seg("DELHI_HUB",     "GWALIOR",         "NH44",          320.0, ts),
            seg("GWALIOR",       "DELHI_HUB",       "NH44",          320.0, ts),
            seg("GWALIOR",       "NAGPUR_CROSSING", "NH44",          460.0, ts),
            seg("NAGPUR_CROSSING","GWALIOR",        "NH44",          460.0, ts),
            seg("NAGPUR_CROSSING","HYDERABAD_HUB",  "NH44",          500.0, ts),
            seg("HYDERABAD_HUB", "NAGPUR_CROSSING", "NH44",          500.0, ts),
            seg("HYDERABAD_HUB", "BANGALORE_RING",  "NH44",          570.0, ts),
            seg("BANGALORE_RING","HYDERABAD_HUB",   "NH44",          570.0, ts),
            seg("MUMBAI_PORT",   "PUNE_DEPOT",      "NH160_BYPASS",  140.0, ts),
            seg("PUNE_DEPOT",    "MUMBAI_PORT",     "NH160_BYPASS",  140.0, ts),
            seg("PUNE_DEPOT",    "BANGALORE_RING",  "NH48",          840.0, ts),
            seg("BANGALORE_RING","PUNE_DEPOT",      "NH48",          840.0, ts),
            seg("PUNE_DEPOT",    "HYDERABAD_HUB",   "NH65",          560.0, ts),
            seg("HYDERABAD_HUB", "PUNE_DEPOT",      "NH65",          560.0, ts),
            seg("MUMBAI_PORT",   "MUMBAI_BYPASS",   "NH3_BYPASS",     25.0, ts),
            seg("MUMBAI_BYPASS", "MUMBAI_PORT",     "NH3_BYPASS",     25.0, ts),
            seg("MUMBAI_BYPASS", "PUNE_DEPOT",      "NH3_BYPASS",    155.0, ts),
            seg("PUNE_DEPOT",    "MUMBAI_BYPASS",   "NH3_BYPASS",    155.0, ts),
            seg("JAIPUR_CORRIDOR","NASHIK_HUB",     "NH52_CORRIDOR", 1000.0, ts),
            seg("NASHIK_HUB",    "JAIPUR_CORRIDOR", "NH52_CORRIDOR", 1000.0, ts),
            seg("AHMEDABAD_HUB", "SURAT_HUB",       "NE1_EXPRESSWAY",250.0, ts),
            seg("SURAT_HUB",     "AHMEDABAD_HUB",   "NE1_EXPRESSWAY",250.0, ts),
            seg("CHENNAI_PORT",  "BANGALORE_RING",  "NH48",          350.0, ts),
            seg("BANGALORE_RING","CHENNAI_PORT",    "NH48",          350.0, ts)
        );
    }

    private static RouteSegment seg(String from, String to, String id, double dist, LocalDateTime ts) {
        return new RouteSegment(null, from, to, id, id + " " + from + "–" + to,
                dist, "CLEAR", 0.0, false, 70.0, 0, "Normal.", ts);
    }

    @BeforeEach
    void setUp() {
        RouteSegmentRepository mockRepo = mock(RouteSegmentRepository.class);
        when(mockRepo.findAll()).thenReturn(staticGraph());
        routeOptimizationService = new RouteOptimizationService(mockRepo);
        // Trigger the initial graph build (normally fired by @Scheduled)
        routeOptimizationService.refreshGraphFromDb();
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
