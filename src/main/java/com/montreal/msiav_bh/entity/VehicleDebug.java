package com.montreal.msiav_bh.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDebug {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String chassis;
    private String renavam;
    private String gravame;
    private String licensePlate;
    private String brand;
    private String model;
    private String color;
    private String detranRegistration;
    private Boolean hasGPS;
    private String registrationState;
    private Integer manufactureYear;
    private Integer modelYear;

    @ManyToOne
    @JoinColumn(name = "contrato_id")
    @JsonBackReference
    private Contract contrato;

}

