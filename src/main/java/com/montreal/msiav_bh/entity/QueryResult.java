package com.montreal.msiav_bh.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
public class QueryResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status_apreensao")
    private String statusApreensao;


    private LocalDateTime dataHoraApreensao;

    private LocalDateTime dataUltimaMovimentacao;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "etapa_atual")
    private String etapaAtual;

    @Column(name = "agendamento_apreensao")
    private LocalDateTime agendamentoApreensao;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    @JsonIgnore
    private VehicleDebug vehicle;


}
