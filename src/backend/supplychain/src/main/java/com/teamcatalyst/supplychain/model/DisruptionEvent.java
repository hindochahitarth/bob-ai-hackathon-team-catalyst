package com.teamcatalyst.supplychain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "disruption_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisruptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String affectedSegment;
    private String description;
    private String severity;

    /** ACTIVE or RESOLVED */
    @Column(nullable = false)
    private String status = "ACTIVE";

    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
}
