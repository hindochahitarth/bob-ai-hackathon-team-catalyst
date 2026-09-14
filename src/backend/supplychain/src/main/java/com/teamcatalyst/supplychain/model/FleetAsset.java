package com.teamcatalyst.supplychain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fleet_assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String assetType;
    private String identifier;
    private String status;
    private String currentLocation;
}
