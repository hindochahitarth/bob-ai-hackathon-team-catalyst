package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Shipment findByTrackingNumber(String trackingNumber);

    /** Returns all shipments with coldChain flag set to true. */
    List<Shipment> findByColdChainTrue();

    /** Returns the count of cold-chain shipments for the dashboard stat tile. */
    long countByColdChainTrue();
}

