package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TempReadingRepository extends JpaRepository<TempReading, Long> {

    List<TempReading> findByShipment(Shipment shipment);

    /**
     * Fetches all readings for a given list of shipments in ONE query.
     * Used by DashboardController and ColdChainController to eliminate the
     * N+1 query pattern (previously: 1 query per cold-chain shipment).
     */
    @Query("SELECT t FROM TempReading t WHERE t.shipment IN :shipments ORDER BY t.recordedAt DESC")
    List<TempReading> findByShipmentIn(List<Shipment> shipments);

    /**
     * Most recent reading per shipment — fetched as a batch.
     * Returns the single latest TempReading row for each shipment.
     */
    @Query("SELECT t FROM TempReading t WHERE t.id IN " +
           "(SELECT MAX(t2.id) FROM TempReading t2 WHERE t2.shipment IN :shipments GROUP BY t2.shipment)")
    List<TempReading> findLatestForShipments(List<Shipment> shipments);

    /** Last N readings for a shipment ordered newest first — for chart history. */
    @Query("SELECT t FROM TempReading t WHERE t.shipment = :shipment ORDER BY t.recordedAt DESC")
    List<TempReading> findTopByShipmentOrdered(Shipment shipment,
                                                org.springframework.data.domain.Pageable pageable);
}
