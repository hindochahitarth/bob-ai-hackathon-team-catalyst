package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Shipment findByTrackingNumber(String trackingNumber);

    /** Returns all shipments with coldChain flag set to true. */
    List<Shipment> findByColdChainTrue();

    /** Returns the count of cold-chain shipments for the dashboard stat tile. */
    long countByColdChainTrue();

    /** Counts shipments by status — avoids loading all records into memory. */
    long countByStatusIgnoreCase(String status);

    /** Fetches all shipments for a given status — avoids full table scan + in-memory filter. */
    List<Shipment> findByStatusIgnoreCase(String status);

    /** Recent N shipments sorted by id desc — for dashboard preview. */
    @Query("SELECT s FROM Shipment s ORDER BY s.id DESC")
    List<Shipment> findTopNOrderedById(org.springframework.data.domain.Pageable pageable);

    /** All shipments sorted newest first — for /shipments page. */
    @Query("SELECT s FROM Shipment s ORDER BY s.id DESC")
    List<Shipment> findAllOrderedByIdDesc();

    /** Active (non-DELIVERED) cold-chain shipments — for cold-chain monitor. */
    @Query("SELECT s FROM Shipment s WHERE s.coldChain = true AND s.status <> 'DELIVERED'")
    List<Shipment> findActiveColdChainShipments();
}

