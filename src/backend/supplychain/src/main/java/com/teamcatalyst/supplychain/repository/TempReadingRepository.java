package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TempReadingRepository extends JpaRepository<TempReading, Long> {

    List<TempReading> findByShipment(Shipment shipment);
}
