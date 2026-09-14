package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.FleetAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FleetAssetRepository extends JpaRepository<FleetAsset, Long> {

    List<FleetAsset> findByStatus(String status);

    long countByStatus(String status);
}
