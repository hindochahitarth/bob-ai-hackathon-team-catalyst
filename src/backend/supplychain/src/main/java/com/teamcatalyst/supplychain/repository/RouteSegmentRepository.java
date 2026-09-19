package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.RouteSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {

    List<RouteSegment> findByBlocked(boolean blocked);

    List<RouteSegment> findBySegmentId(String segmentId);

    List<RouteSegment> findByFromNodeOrToNode(String fromNode, String toNode);

    /** All segments with congestion above a threshold — for the live network map. */
    @Query("SELECT s FROM RouteSegment s WHERE s.congestionScore >= :threshold ORDER BY s.congestionScore DESC")
    List<RouteSegment> findHighCongestion(double threshold);

    /** Segments currently in a named incident state. */
    List<RouteSegment> findByIncidentTypeNot(String incidentType);

    /** All segments sorted by congestion score descending — for the dashboard feed. */
    @Query("SELECT s FROM RouteSegment s ORDER BY s.congestionScore DESC")
    List<RouteSegment> findAllOrderedByCongestion();
}
