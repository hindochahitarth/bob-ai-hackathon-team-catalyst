package com.teamcatalyst.supplychain.service;

import com.teamcatalyst.supplychain.model.RouteSegment;
import com.teamcatalyst.supplychain.model.RerouteResult;
import com.teamcatalyst.supplychain.repository.RouteSegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RouteOptimizationService — DB-backed, live-refreshed Dijkstra router.
 *
 * The network graph is no longer hard-coded in Java. All edges are stored
 * as RouteSegment rows. Every 5 seconds the service rebuilds its in-memory
 * adjacency list from the database, so any congestion score, blockage, or
 * incident change made by LiveSimulationService is automatically picked up
 * by the next reroute calculation.
 *
 * Dijkstra edge weight = segment.effectiveWeight()
 *   = distanceKm × (1 + congestionScore × 2)
 *
 * A blocked segment (segment.blocked = true) is excluded entirely.
 * An explicitly passed blockedSegments/blockedNodes set adds further
 * exclusions (from disruption events).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

    private final RouteSegmentRepository segmentRepo;

    private static final double ASSUMED_AVERAGE_SPEED_KMH = 50.0;

    // ── In-memory graph (rebuilt from DB every 5 s) ───────────────────────────
    private volatile Map<String, List<Edge>> graphCache = new ConcurrentHashMap<>();

    /** Directed edge used in Dijkstra. */
    public record Edge(String from, String to, double distanceKm,
                       double effectiveWeight, String segmentId) {}

    private record NodeEntry(String node, double cost) implements Comparable<NodeEntry> {
        @Override public int compareTo(NodeEntry o) { return Double.compare(this.cost, o.cost); }
    }

    public record CarrierProfile(
            String name, boolean available,
            double reliabilityScore, double costPerKm,
            Set<String> cargoSpecialties, Set<String> supportedRegions) {}

    private record CarrierEval(String name, double score) {}

    private final List<CarrierProfile> carriers = buildCarrierPool();

    // ── Graph refresh — every 5 seconds ──────────────────────────────────────

    @Scheduled(fixedDelay = 5000)
    public void refreshGraphFromDb() {
        List<RouteSegment> segments = segmentRepo.findAll();
        Map<String, List<Edge>> newGraph = new HashMap<>();
        for (RouteSegment seg : segments) {
            if (seg.isBlocked()) continue; // hard-blocked edges excluded
            double w = seg.effectiveWeight();
            newGraph.computeIfAbsent(seg.getFromNode(), k -> new ArrayList<>())
                    .add(new Edge(seg.getFromNode(), seg.getToNode(), seg.getDistanceKm(), w, seg.getSegmentId()));
            // Bidirectional
            newGraph.computeIfAbsent(seg.getToNode(), k -> new ArrayList<>())
                    .add(new Edge(seg.getToNode(), seg.getFromNode(), seg.getDistanceKm(), w, seg.getSegmentId()));
        }
        graphCache = newGraph;
    }

    // ── Public reroute API ────────────────────────────────────────────────────

    /**
     * Finds the optimal alternate route using Dijkstra over the live DB-backed graph.
     *
     * @param originInput     Shipment origin (city name or graph node ID)
     * @param destInput       Shipment destination
     * @param blockedSegments Additional segment IDs to exclude (from disruption event)
     * @param blockedNodes    Additional node IDs to exclude
     * @param cargoType       Cargo type for carrier selection
     */
    public RerouteResult findOptimalRoute(
            String originInput, String destInput,
            Set<String> blockedSegments, Set<String> blockedNodes,
            String cargoType) {

        String origin = resolveNodeId(originInput);
        String dest   = resolveNodeId(destInput);

        if (origin == null || !graphCache.containsKey(origin)) {
            return RerouteResult.builder().success(false)
                    .summary("Origin '" + originInput + "' not found in live logistics network.").build();
        }
        if (dest == null || !graphCache.containsKey(dest)) {
            return RerouteResult.builder().success(false)
                    .summary("Destination '" + destInput + "' not found in live logistics network.").build();
        }

        // Original path (no extra blocks)
        DijkstraOut normal = dijkstra(origin, dest, Collections.emptySet(), Collections.emptySet());

        // Alternate path (with disruption blocks)
        Set<String> normSeg  = normalise(blockedSegments);
        Set<String> normNode = new HashSet<>();
        if (blockedNodes != null) blockedNodes.forEach(n -> normNode.add(resolveNodeId(n)));

        DijkstraOut alt = dijkstra(origin, dest, normSeg, normNode);

        if (!alt.found()) {
            return RerouteResult.builder()
                    .success(false)
                    .originalPath(normal.path())
                    .originalDistanceKm(normal.dist())
                    .alternatePath(Collections.emptyList())
                    .summary("No feasible bypass found — all alternate corridors blocked or congested.")
                    .riskLevel("HIGH")
                    .build();
        }

        double origDist  = normal.found() ? normal.dist() : alt.dist();
        double altDist   = alt.dist();
        double deltaKm   = Math.max(0, Math.round((altDist - origDist) * 10) / 10.0);
        double delayHrs  = Math.round((deltaKm / ASSUMED_AVERAGE_SPEED_KMH) * 10) / 10.0;
        String bypassSeg = findFirstBypass(alt.path());
        CarrierEval carrier = bestCarrier(cargoType, altDist);
        String risk = deltaKm <= 100 ? "LOW" : deltaKm <= 250 ? "MEDIUM" : "HIGH";

        return RerouteResult.builder()
                .success(true)
                .originalPath(normal.path())
                .alternatePath(alt.path())
                .originalDistanceKm(origDist)
                .alternateDistanceKm(altDist)
                .deltaKm(deltaKm)
                .estimatedDelayHours(delayHrs)
                .recommendedCarrier(carrier.name())
                .carrierScore(carrier.score())
                .riskLevel(risk)
                .bypassSegment(bypassSeg)
                .summary(String.format("Live Dijkstra bypass via %s (%s). +%.0f km (~%.1fh delay).",
                        bypassSeg, String.join(" → ", alt.path()), deltaKm, delayHrs))
                .build();
    }

    // ── Dijkstra core ─────────────────────────────────────────────────────────

    private record DijkstraOut(boolean found, List<String> path, double dist) {}

    private DijkstraOut dijkstra(String src, String tgt,
                                  Set<String> blockedSegs, Set<String> blockedNodes) {
        if (blockedNodes.contains(src) || blockedNodes.contains(tgt))
            return new DijkstraOut(false, List.of(), Double.MAX_VALUE);

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<NodeEntry> pq = new PriorityQueue<>();
        dist.put(src, 0.0);
        pq.add(new NodeEntry(src, 0.0));
        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            NodeEntry cur = pq.poll();
            if (visited.contains(cur.node())) continue;
            visited.add(cur.node());
            if (cur.node().equals(tgt)) break;

            for (Edge e : graphCache.getOrDefault(cur.node(), List.of())) {
                if (blockedNodes.contains(e.to())) continue;
                if (segBlocked(e.segmentId(), blockedSegs))  continue;
                double nd = dist.get(cur.node()) + e.effectiveWeight();
                if (nd < dist.getOrDefault(e.to(), Double.MAX_VALUE)) {
                    dist.put(e.to(), nd);
                    prev.put(e.to(), cur.node());
                    pq.add(new NodeEntry(e.to(), nd));
                }
            }
        }

        if (!dist.containsKey(tgt)) return new DijkstraOut(false, List.of(), Double.MAX_VALUE);

        List<String> path = new ArrayList<>();
        for (String s = tgt; s != null; s = prev.get(s)) path.add(0, s);
        return new DijkstraOut(true, path, dist.get(tgt));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean segBlocked(String edgeSeg, Set<String> blocked) {
        if (blocked == null || blocked.isEmpty() || edgeSeg == null) return false;
        String e = edgeSeg.toUpperCase();
        return blocked.stream().anyMatch(b -> e.contains(b) || b.contains(e));
    }

    private Set<String> normalise(Set<String> in) {
        Set<String> out = new HashSet<>();
        if (in != null) in.stream().filter(s -> s != null && !s.isBlank())
                          .forEach(s -> out.add(s.trim().toUpperCase()));
        return out;
    }

    private String findFirstBypass(List<String> path) {
        if (path == null || path.size() < 2) return "ALTERNATE_CORRIDOR";
        for (int i = 0; i < path.size() - 1; i++) {
            String u = path.get(i), v = path.get(i + 1);
            for (Edge e : graphCache.getOrDefault(u, List.of())) {
                if (e.to().equals(v) && e.segmentId().contains("BYPASS"))
                    return e.segmentId();
            }
        }
        return "ALTERNATE_CORRIDOR";
    }

    // ── Node ID resolution (city name → graph ID) ─────────────────────────────

    public String resolveNodeId(String input) {
        if (input == null || input.isBlank()) return null;
        String c = input.trim().toUpperCase();
        if (graphCache.containsKey(c)) return c;
        if (c.contains("MUMBAI"))    return c.contains("BYPASS") ? "MUMBAI_BYPASS" : "MUMBAI_PORT";
        if (c.contains("DELHI"))     return "DELHI_HUB";
        if (c.contains("CHENNAI"))   return "CHENNAI_PORT";
        if (c.contains("PUNE"))      return "PUNE_DEPOT";
        if (c.contains("SURAT"))     return "SURAT_HUB";
        if (c.contains("BANGALORE") || c.contains("BENGALURU")) return "BANGALORE_RING";
        if (c.contains("KOLKATA"))   return "KOLKATA_PORT";
        if (c.contains("NAGPUR"))    return "NAGPUR_CROSSING";
        if (c.contains("NASHIK"))    return "NASHIK_HUB";
        if (c.contains("JAIPUR"))    return "JAIPUR_CORRIDOR";
        if (c.contains("GWALIOR"))   return "GWALIOR";
        if (c.contains("HYDERABAD")) return "HYDERABAD_HUB";
        if (c.contains("AHMEDABAD")) return "AHMEDABAD_HUB";
        if (c.contains("GOA"))       return "GOA_COASTAL";
        if (c.contains("KOCHI") || c.contains("COCHIN")) return "KOCHI_PORT";
        return c;
    }

    // ── Carrier pool (static profiles — could also be a DB table) ─────────────

    private static List<CarrierProfile> buildCarrierPool() {
        return List.of(
            new CarrierProfile("BlueDart Cargo", true, 0.96, 18.0,
                Set.of("Pharmaceuticals","Vaccines","Perishables","Electronics"),
                Set.of("NORTH","WEST","SOUTH","EAST","CENTRAL")),
            new CarrierProfile("TCI Freight", true, 0.92, 14.5,
                Set.of("General","Electronics","Textiles","Automotive Parts"),
                Set.of("WEST","NORTH","SOUTH","CENTRAL")),
            new CarrierProfile("Delhivery Logistics", true, 0.88, 13.0,
                Set.of("Electronics","FMCG","Textiles","Perishables"),
                Set.of("WEST","NORTH","SOUTH","EAST","CENTRAL")),
            new CarrierProfile("Safexpress", true, 0.91, 15.0,
                Set.of("Pharmaceuticals","Perishables","General"),
                Set.of("WEST","SOUTH","CENTRAL")),
            new CarrierProfile("VRL Logistics", true, 0.86, 12.5,
                Set.of("Heavy Machinery","Automotive Parts","Textiles","General"),
                Set.of("SOUTH","WEST","CENTRAL","NORTH")),
            new CarrierProfile("Gati-KWE", false, 0.89, 13.8,
                Set.of("Perishables","Textiles","General"),
                Set.of("EAST","CENTRAL","NORTH","WEST"))
        );
    }

    private CarrierEval bestCarrier(String cargoType, double distKm) {
        double minCost = carriers.stream().mapToDouble(CarrierProfile::costPerKm).min().orElse(12.0);
        CarrierProfile best = null;
        double bestScore = -1;
        for (CarrierProfile c : carriers) {
            if (!c.available()) continue;
            double reliability = c.reliabilityScore() * 40.0;
            double availability = 30.0;
            double costNorm = (minCost / c.costPerKm()) * 20.0;
            boolean match = cargoType != null && c.cargoSpecialties().stream()
                    .anyMatch(s -> s.equalsIgnoreCase(cargoType) || cargoType.toUpperCase().contains(s.toUpperCase()));
            double cargo = match ? 10.0 : 5.0;
            double score = Math.round((reliability + availability + costNorm + cargo) * 10.0) / 10.0;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best != null ? new CarrierEval(best.name(), bestScore)
                            : new CarrierEval("TCI Freight (Default)", 75.0);
    }
}
