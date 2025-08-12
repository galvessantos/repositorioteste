package com.montreal.msiav_bh.repository;

import com.montreal.msiav_bh.entity.VehicleCache;
import com.montreal.msiav_bh.entity.VehicleDebug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleDebugRepository extends JpaRepository<VehicleDebug, Long> {

    Optional<VehicleDebug> findByLicensePlate(String licensePlate);
}
