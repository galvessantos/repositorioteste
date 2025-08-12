package com.montreal.msiav_bh.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicle_cache_unique_index", 
       uniqueConstraints = {
           @UniqueConstraint(name = "unique_contrato_hash", columnNames = {"contrato_hash"}),
           @UniqueConstraint(name = "unique_placa_hash", columnNames = {"placa_hash"}),
           @UniqueConstraint(name = "unique_contrato_placa_hash", columnNames = {"contrato_placa_hash"})
       },
       indexes = {
           @Index(name = "idx_vehicle_cache_id", columnList = "vehicle_cache_id"),
           @Index(name = "idx_contrato_hash", columnList = "contrato_hash"),
           @Index(name = "idx_placa_hash", columnList = "placa_hash")
       })
public class VehicleCacheUniqueIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_cache_id", nullable = false, unique = true)
    private Long vehicleCacheId;

    @Column(name = "contrato_hash", length = 64)
    private String contratoHash;

    @Column(name = "placa_hash", length = 64)
    private String placaHash;

    @Column(name = "contrato_placa_hash", length = 64)
    private String contratoPlacaHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}