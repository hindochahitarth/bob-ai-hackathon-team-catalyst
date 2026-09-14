package com.teamcatalyst.supplychain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String trackingNumber;
    private String cargoType;
    private boolean coldChain;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    private String status;
    private String currentLocation;
}
