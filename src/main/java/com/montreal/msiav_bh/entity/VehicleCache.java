package com.montreal.msiav_bh.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicle_cache", indexes = {
        @Index(name = "idx_placa", columnList = "placa"),
        @Index(name = "idx_contrato", columnList = "contrato"),
        @Index(name = "idx_protocolo", columnList = "protocolo"),
        @Index(name = "idx_api_sync_date", columnList = "api_sync_date"),
        @Index(name = "idx_unique_vehicle", columnList = "contrato, placa", unique = true),
        @Index(name = "idx_contrato_hash", columnList = "contrato_hash"),
        @Index(name = "idx_placa_hash", columnList = "placa_hash"),
        @Index(name = "idx_unique_vehicle_hash", columnList = "contrato_hash, placa_hash", unique = true)
})
public class VehicleCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private Long externalId;

    @Column(name = "credor")
    private String credor;

    @Column(name = "data_pedido")
    private LocalDate dataPedido;

    @Column(name = "contrato", columnDefinition = "TEXT")
    @JsonIgnore
    private String contrato;

    @Column(name = "contrato_hash", length = 64)
    private String contratoHash;

    @Column(name = "placa", columnDefinition = "TEXT")
    @JsonIgnore
    private String placa;

    @Column(name = "placa_hash", length = 64)
    private String placaHash;

    @Column(name = "modelo")
    private String modelo;

    @Column(name = "uf")
    private String uf;

    @Column(name = "cidade")
    private String cidade;

    @Column(name = "cpf_devedor")
    private String cpfDevedor;

    @Column(name = "protocolo")
    private String protocolo;

    @Column(name = "etapa_atual")
    private String etapaAtual;

    @Column(name = "status_apreensao")
    private String statusApreensao;

    @Column(name = "ultima_movimentacao")
    private LocalDate ultimaMovimentacao;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "api_sync_date")
    private LocalDateTime apiSyncDate;
}