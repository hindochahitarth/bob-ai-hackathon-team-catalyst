package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Shipment findByTrackingNumber(String trackingNumber);
}
