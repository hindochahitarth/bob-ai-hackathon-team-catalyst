package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.RerouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Graph-based route optimization service using Dijkstra's shortest-path algorithm.
 * Dynamically models India's commercial freight network and calculates optimal bypass
 * corridors and alternate carrier allocations when disruptions occur.
 */
@Service
@Slf4j
public class RouteOptimizationService {

    // Assumed average speed for commercial transport fleet (km/h) for transit estimation
    private static final double ASSUMED_AVERAGE_SPEED_KMH = 50.0;

    /** Directed edge representing a transport corridor connecting two logistics nodes. */
    public record Edge(String from, String to, double distanceKm, String segmentId) {}

    /** State element stored in Dijkstra's priority queue. */
    private record NodeEntry(String node, double distance) implements Comparable<NodeEntry> {
        @Override
        public int compareTo(NodeEntry other) {
            return Double.compare(this.distance, other.distance);
        }
    }

    /** Carrier profile for multi-criteria recommendation. */
    public record CarrierProfile(
            String name,
            boolean available,
            double reliabilityScore, // 0.0 - 1.0 (e.g. 0.95 = 95%)
            double costPerKm,        // INR per km
            Set<String> cargoSpecialties,
            Set<String> supportedRegions
    ) {}

    // In-memory logistics graph adjacency list: node -> list of outgoing edges
    private final Map<String, List<Edge>> adjacencyList = new HashMap<>();

    // Available carrier pool
    private final List<CarrierProfile> carriers = new ArrayList<>();

    public RouteOptimizationService() {
        initNetworkGraph();
        initCarrierPool();
    }

    /**
     * Initializes the Indian freight network graph with primary transit corridors
     * and strategic alternate bypass routes.
     */
    private void initNetworkGraph() {
        // === PRIMARY NH-48 WESTERN FREIGHT CORRIDOR (Mumbai - Delhi) ===
        addBidirectionalEdge("MUMBAI_PORT", "SURAT_HUB", 280.0, "NH48");
        addBidirectionalEdge("SURAT_HUB", "JAIPUR_CORRIDOR", 740.0, "NH48");
        addBidirectionalEdge("JAIPUR_CORRIDOR", "DELHI_HUB", 400.0, "NH48");

        // === SOUTHERN FREIGHT CORRIDORS (Chennai - Pune - Bangalore) ===
        addBidirectionalEdge("CHENNAI_PORT", "BANGALORE_RING", 350.0, "NH48");
        addBidirectionalEdge("BANGALORE_RING", "PUNE_DEPOT", 840.0, "NH48");

        // === SURAT - BANGALORE EXPRESS ROUTE ===
        addBidirectionalEdge("SURAT_HUB", "MUMBAI_BYPASS", 270.0, "NH48");
        addBidirectionalEdge("MUMBAI_BYPASS", "PUNE_DEPOT", 150.0, "NH48");
        addBidirectionalEdge("PUNE_DEPOT", "BANGALORE_RING", 830.0, "NH48");

        // === EAST-WEST FREIGHT CORRIDOR (Kolkata - Mumbai via Central India) ===
        addBidirectionalEdge("KOLKATA_PORT", "NAGPUR_CROSSING", 1020.0, "NH53");
        addBidirectionalEdge("NAGPUR_CROSSING", "NASHIK_HUB", 740.0, "NH53");
        addBidirectionalEdge("NASHIK_HUB", "MUMBAI_PORT", 200.0, "NH53");

        // === NORTH-SOUTH CENTRAL SPINE (Delhi - Bangalore via NH-44) ===
        addBidirectionalEdge("DELHI_HUB", "GWALIOR", 330.0, "NH44");
        addBidirectionalEdge("GWALIOR", "NAGPUR_CROSSING", 670.0, "NH44");
        addBidirectionalEdge("NAGPUR_CROSSING", "HYDERABAD_HUB", 500.0, "NH44");
        addBidirectionalEdge("HYDERABAD_HUB", "BANGALORE_RING", 650.0, "NH44");

        // === WEST COASTAL CORRIDOR (Ahmedabad - Kochi) ===
        addBidirectionalEdge("AHMEDABAD_HUB", "MUMBAI_BYPASS", 530.0, "NH48");
        addBidirectionalEdge("MUMBAI_BYPASS", "GOA_COASTAL", 580.0, "NH66");
        addBidirectionalEdge("GOA_COASTAL", "KOCHI_PORT", 670.0, "NH66");

        // === STRATEGIC ALTERNATE BYPASS CORRIDORS (Activated during disruptions) ===
        // Bypass 1: Mumbai to Nashik / Central India bypassing coastal NH48
        addBidirectionalEdge("MUMBAI_PORT", "NASHIK_HUB", 190.0, "NH160_BYPASS");
        addBidirectionalEdge("MUMBAI_BYPASS", "NASHIK_HUB", 165.0, "NH3_BYPASS");

        // Bypass 2: Surat to Nashik connecting Western & Central spines
        addBidirectionalEdge("SURAT_HUB", "NASHIK_HUB", 240.0, "SH10_BYPASS");

        // Bypass 3: Nashik to Pune link bypassing Mumbai bottleneck
        addBidirectionalEdge("NASHIK_HUB", "PUNE_DEPOT", 210.0, "SH60_BYPASS");

        // Bypass 4: Samruddhi Super Expressway connecting Pune/Nashik directly to Nagpur
        addBidirectionalEdge("PUNE_DEPOT", "NAGPUR_CROSSING", 710.0, "SAMRUDDHI_EXPRESSWAY");

        // Bypass 5: Central NH-52 highway connecting Nashik to Gwalior / Delhi
        addBidirectionalEdge("NASHIK_HUB", "GWALIOR", 990.0, "NH52_CORRIDOR");

        // Bypass 6: Gwalior to Jaipur link
        addBidirectionalEdge("GWALIOR", "JAIPUR_CORRIDOR", 320.0, "NH21_BYPASS");

        // Bypass 7: Ahmedabad directly to Jaipur corridor
        addBidirectionalEdge("AHMEDABAD_HUB", "JAIPUR_CORRIDOR", 660.0, "NH58_BYPASS");

        // Bypass 8: East coast Chennai - Hyderabad - Pune bypass
        addBidirectionalEdge("CHENNAI_PORT", "HYDERABAD_HUB", 630.0, "NH16_COASTAL");
        addBidirectionalEdge("HYDERABAD_HUB", "PUNE_DEPOT", 620.0, "NH65_CORRIDOR");

        // Bypass 9: Bangalore to Kochi via Salem/Coimbatore
        addBidirectionalEdge("BANGALORE_RING", "KOCHI_PORT", 540.0, "NH544_CORRIDOR");

        // Bypass 10: Ahmedabad Express link to Surat
        addBidirectionalEdge("SURAT_HUB", "AHMEDABAD_HUB", 260.0, "NE1_EXPRESSWAY");
    }

    /**
     * Initializes verified commercial carriers with real-world operating profiles.
     */
    private void initCarrierPool() {
        carriers.add(new CarrierProfile(
                "BlueDart Cargo",
                true,
                0.96, // 96% on-time reliability
                18.0, // INR/km
                Set.of("Pharmaceuticals", "Vaccines", "Perishables", "Electronics"),
                Set.of("NORTH", "WEST", "SOUTH", "EAST", "CENTRAL")
        ));

        carriers.add(new CarrierProfile(
                "TCI Freight",
                true,
                0.92, // 92% reliability
                14.5,
                Set.of("General", "Electronics", "Textiles", "Automotive Parts"),
                Set.of("WEST", "NORTH", "SOUTH", "CENTRAL")
        ));

        carriers.add(new CarrierProfile(
                "Delhivery Logistics",
                true,
                0.88, // 88% reliability
                13.0,
                Set.of("Electronics", "FMCG", "Textiles", "Perishables"),
                Set.of("WEST", "NORTH", "SOUTH", "EAST", "CENTRAL")
        ));

        carriers.add(new CarrierProfile(
                "Safexpress",
                true,
                0.91, // 91% reliability
                15.0,
                Set.of("Pharmaceuticals", "Perishables", "General"),
                Set.of("WEST", "SOUTH", "CENTRAL")
        ));

        carriers.add(new CarrierProfile(
                "VRL Logistics",
                true,
                0.86, // 86% reliability
                12.5,
                Set.of("Heavy Machinery", "Automotive Parts", "Textiles", "General"),
                Set.of("SOUTH", "WEST", "CENTRAL", "NORTH")
        ));

        carriers.add(new CarrierProfile(
                "Gati-KWE",
                false, // Currently at full capacity / unavailable for testing exclusion
                0.89,
                13.8,
                Set.of("Perishables", "Textiles", "General"),
                Set.of("EAST", "CENTRAL", "NORTH", "WEST")
        ));
    }

    private void addBidirectionalEdge(String u, String v, double distKm, String segmentId) {
        adjacencyList.computeIfAbsent(u, k -> new ArrayList<>()).add(new Edge(u, v, distKm, segmentId));
        adjacencyList.computeIfAbsent(v, k -> new ArrayList<>()).add(new Edge(v, u, distKm, segmentId));
    }

    /**
     * Resolves human-readable city names or waypoint strings to standard graph node IDs.
     */
    public String resolveNodeId(String input) {
        if (input == null || input.isBlank()) return null;
        String clean = input.trim().toUpperCase();

        if (adjacencyList.containsKey(clean)) {
            return clean;
        }

        if (clean.contains("MUMBAI")) return clean.contains("BYPASS") ? "MUMBAI_BYPASS" : "MUMBAI_PORT";
        if (clean.contains("DELHI")) return "DELHI_HUB";
        if (clean.contains("CHENNAI")) return "CHENNAI_PORT";
        if (clean.contains("PUNE")) return "PUNE_DEPOT";
        if (clean.contains("SURAT")) return "SURAT_HUB";
        if (clean.contains("BANGALORE") || clean.contains("BENGALURU")) return "BANGALORE_RING";
        if (clean.contains("KOLKATA")) return "KOLKATA_PORT";
        if (clean.contains("NAGPUR")) return "NAGPUR_CROSSING";
        if (clean.contains("NASHIK")) return "NASHIK_HUB";
        if (clean.contains("JAIPUR")) return "JAIPUR_CORRIDOR";
        if (clean.contains("GWALIOR")) return "GWALIOR";
        if (clean.contains("HYDERABAD")) return "HYDERABAD_HUB";
        if (clean.contains("AHMEDABAD")) return "AHMEDABAD_HUB";
        if (clean.contains("GOA")) return "GOA_COASTAL";
        if (clean.contains("KOCHI") || clean.contains("COCHIN")) return "KOCHI_PORT";

        return clean;
    }

    /**
     * Finds the optimal alternate route using Dijkstra's algorithm while excluding
     * disrupted highway segments and blocked nodes.
     *
     * @param originInput     Shipment origin (city or waypoint)
     * @param destInput       Shipment destination (city or waypoint)
     * @param blockedSegments Set of corridor segment IDs to avoid (e.g. "NH48")
     * @param blockedNodes    Set of node IDs to avoid (e.g. "MUMBAI_PORT")
     * @param cargoType       Cargo type for carrier suitability evaluation
     * @return RerouteResult with shortest bypass path, delta distance, and recommended carrier
     */
    public RerouteResult findOptimalRoute(
            String originInput,
            String destInput,
            Set<String> blockedSegments,
            Set<String> blockedNodes,
            String cargoType
    ) {
        String origin = resolveNodeId(originInput);
        String destination = resolveNodeId(destInput);

        if (origin == null || !adjacencyList.containsKey(origin)) {
            log.warn("Origin '{}' not found in logistics graph.", originInput);
            return RerouteResult.builder()
                    .success(false)
                    .summary("Origin '" + originInput + "' not found in logistics network.")
                    .build();
        }

        if (destination == null || !adjacencyList.containsKey(destination)) {
            log.warn("Destination '{}' not found in logistics graph.", destInput);
            return RerouteResult.builder()
                    .success(false)
                    .summary("Destination '" + destInput + "' not found in logistics network.")
                    .build();
        }

        // 1. Calculate original shortest path on intact network (no disruptions)
        DijkstraOutput normalRoute = executeDijkstra(origin, destination, Collections.emptySet(), Collections.emptySet());

        // 2. Calculate alternate shortest path avoiding blocked corridors and nodes
        Set<String> normalizedBlockedSegments = new HashSet<>();
        if (blockedSegments != null) {
            for (String s : blockedSegments) {
                if (s != null && !s.isBlank()) normalizedBlockedSegments.add(s.trim().toUpperCase());
            }
        }

        Set<String> normalizedBlockedNodes = new HashSet<>();
        if (blockedNodes != null) {
            for (String n : blockedNodes) {
                if (n != null && !n.isBlank()) normalizedBlockedNodes.add(resolveNodeId(n));
            }
        }

        DijkstraOutput alternateRoute = executeDijkstra(origin, destination, normalizedBlockedSegments, normalizedBlockedNodes);

        if (!alternateRoute.found()) {
            log.info("No feasible alternate path between {} and {} with blocked segments {}", origin, destination, normalizedBlockedSegments);
            return RerouteResult.builder()
                    .success(false)
                    .originalPath(normalRoute.path())
                    .originalDistanceKm(normalRoute.totalDistance())
                    .alternatePath(Collections.emptyList())
                    .alternateDistanceKm(0.0)
                    .deltaKm(0.0)
                    .estimatedDelayHours(0.0)
                    .summary("No feasible alternate bypass route found avoiding disrupted corridor.")
                    .riskLevel("HIGH")
                    .build();
        }

        // 3. Compute metric differences
        double origDist = normalRoute.found() ? normalRoute.totalDistance() : alternateRoute.totalDistance();
        double altDist = alternateRoute.totalDistance();
        double deltaKm = Math.max(0.0, Math.round((altDist - origDist) * 10.0) / 10.0);
        double delayHours = Math.round((deltaKm / ASSUMED_AVERAGE_SPEED_KMH) * 10.0) / 10.0;

        // 4. Determine key bypass segment utilized
        String bypassSegment = findFirstBypassSegment(alternateRoute.path());

        // 5. Select best carrier using multi-factor optimization
        CarrierEvaluation bestCarrier = evaluateBestCarrier(cargoType, altDist);

        // 6. Determine risk level based on additional travel distance
        String riskLevel = deltaKm <= 100.0 ? "LOW" : (deltaKm <= 250.0 ? "MEDIUM" : "HIGH");

        String summary = String.format("Dijkstra identified %s bypass route (%s). Delta: +%.0f km (~%.1fh delay).",
                bypassSegment, String.join(" → ", alternateRoute.path()), deltaKm, delayHours);

        return RerouteResult.builder()
                .success(true)
                .originalPath(normalRoute.path())
                .alternatePath(alternateRoute.path())
                .originalDistanceKm(origDist)
                .alternateDistanceKm(altDist)
                .deltaKm(deltaKm)
                .estimatedDelayHours(delayHours)
                .recommendedCarrier(bestCarrier.carrierName())
                .carrierScore(bestCarrier.score())
                .riskLevel(riskLevel)
                .bypassSegment(bypassSegment)
                .summary(summary)
                .build();
    }

    /**
     * Executes pure Dijkstra algorithm using a PriorityQueue, distance map, and predecessor map.
     */
    private DijkstraOutput executeDijkstra(
            String source,
            String target,
            Set<String> blockedSegments,
            Set<String> blockedNodes
    ) {
        // If the source or target itself is blocked, no path can exist
        if (blockedNodes.contains(source) || blockedNodes.contains(target)) {
            return new DijkstraOutput(false, Collections.emptyList(), Double.POSITIVE_INFINITY);
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<NodeEntry> pq = new PriorityQueue<>();

        dist.put(source, 0.0);
        pq.add(new NodeEntry(source, 0.0));

        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            NodeEntry current = pq.poll();
            String u = current.node();

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equals(target)) {
                break; // Target reached with minimum distance
            }

            List<Edge> edges = adjacencyList.getOrDefault(u, Collections.emptyList());
            for (Edge edge : edges) {
                String v = edge.to();

                // Skip blocked nodes
                if (blockedNodes.contains(v)) {
                    continue;
                }

                // Skip edges that match any blocked segment ID (case-insensitive substring/equality)
                if (isSegmentBlocked(edge.segmentId(), blockedSegments)) {
                    continue;
                }

                double newDist = dist.get(u) + edge.distanceKm();
                if (newDist < dist.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    dist.put(v, newDist);
                    prev.put(v, u);
                    pq.add(new NodeEntry(v, newDist));
                }
            }
        }

        if (!dist.containsKey(target)) {
            return new DijkstraOutput(false, Collections.emptyList(), Double.POSITIVE_INFINITY);
        }

        // Reconstruct path from predecessors
        List<String> path = new ArrayList<>();
        String step = target;
        while (step != null) {
            path.add(0, step);
            step = prev.get(step);
        }

        return new DijkstraOutput(true, path, dist.get(target));
    }

    private boolean isSegmentBlocked(String edgeSegmentId, Set<String> blockedSegments) {
        if (edgeSegmentId == null || blockedSegments == null || blockedSegments.isEmpty()) {
            return false;
        }
        String normalizedEdgeSeg = edgeSegmentId.toUpperCase();
        for (String blocked : blockedSegments) {
            if (normalizedEdgeSeg.contains(blocked) || blocked.contains(normalizedEdgeSeg)) {
                return true;
            }
        }
        return false;
    }

    private String findFirstBypassSegment(List<String> path) {
        if (path == null || path.size() < 2) return "STANDARD";
        for (int i = 0; i < path.size() - 1; i++) {
            String u = path.get(i);
            String v = path.get(i + 1);
            List<Edge> edges = adjacencyList.getOrDefault(u, Collections.emptyList());
            for (Edge e : edges) {
                if (e.to().equals(v) && e.segmentId().contains("BYPASS")) {
                    return e.segmentId();
                }
            }
        }
        return "ALTERNATE_CORRIDOR";
    }

    /**
     * Carrier evaluation using a deterministic multi-criteria weighted scoring algorithm:
     * - 40% Reliability Score
     * - 30% Availability
     * - 20% Cost Efficiency (inverse normalized relative to max cost)
     * - 10% Cargo Specialization Suitability
     */
    private record CarrierEvaluation(String carrierName, double score) {}

    private CarrierEvaluation evaluateBestCarrier(String cargoType, double totalDistanceKm) {
        CarrierProfile bestCarrier = null;
        double bestScore = -1.0;

        double minCost = carriers.stream().mapToDouble(CarrierProfile::costPerKm).min().orElse(12.0);
        double maxCost = carriers.stream().mapToDouble(CarrierProfile::costPerKm).max().orElse(20.0);

        for (CarrierProfile c : carriers) {
            // Requirement 1: Exclude unavailable carriers
            if (!c.available()) {
                continue;
            }

            // Component 1: Reliability (40% weight) -> scale 0 to 40
            double reliabilityComp = c.reliabilityScore() * 40.0;

            // Component 2: Availability (30% weight) -> available is guaranteed 30
            double availabilityComp = 30.0;

            // Component 3: Cost efficiency (20% weight) -> cost ratio relative to lowest benchmark
            double costNorm = minCost / c.costPerKm();
            double costComp = costNorm * 20.0;

            // Component 4: Cargo Suitability (10% weight) -> 10 if specialized, 5 if general
            boolean cargoMatch = cargoType != null && c.cargoSpecialties().stream()
                    .anyMatch(spec -> spec.equalsIgnoreCase(cargoType) || cargoType.toUpperCase().contains(spec.toUpperCase()));
            double cargoComp = cargoMatch ? 10.0 : 5.0;

            double totalScore = Math.round((reliabilityComp + availabilityComp + costComp + cargoComp) * 10.0) / 10.0;

            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestCarrier = c;
            }
        }

        if (bestCarrier == null) {
            return new CarrierEvaluation("TCI Freight (Default)", 75.0);
        }

        return new CarrierEvaluation(bestCarrier.name(), bestScore);
    }

    private record DijkstraOutput(boolean found, List<String> path, double totalDistance) {}
}
