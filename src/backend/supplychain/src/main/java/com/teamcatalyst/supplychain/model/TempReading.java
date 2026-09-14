package com.teamcatalyst.supplychain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "temp_readings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TempReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    private double temperatureCelsius;
    private LocalDateTime recordedAt;
}
