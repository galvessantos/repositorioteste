package com.montreal.msiav_bh.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "creditors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Creditor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String cnpj;
    private String email;
    private String stateRegistration;
    private String address;
    private String phone;

    @OneToOne
    @JoinColumn(name = "contrato_id")
    @JsonBackReference
    private Contract contrato;
}
