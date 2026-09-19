package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, Long> {

    /** All active disruptions — avoids loading resolved ones into memory. */
    List<DisruptionEvent> findByStatus(String status);

    /** Count active disruptions — DB-level aggregation, no full table load. */
    long countByStatus(String status);

    /** Most severe active disruptions, newest first — for dashboard preview. */
    @Query("SELECT d FROM DisruptionEvent d WHERE d.status = 'ACTIVE' ORDER BY " +
           "CASE d.severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, d.startedAt DESC")
    List<DisruptionEvent> findActiveOrderedBySeverity();

    /** All disruptions ordered by startedAt descending — for full list page. */
    @Query("SELECT d FROM DisruptionEvent d ORDER BY d.startedAt DESC")
    List<DisruptionEvent> findAllOrderedByStartedAtDesc();
}
