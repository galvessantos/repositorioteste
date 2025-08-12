package com.montreal.msiav_bh.repository;

import com.montreal.msiav_bh.entity.ProbableAddressDebug;
import com.montreal.msiav_bh.entity.VehicleDebug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProbableAddressDebugRepository extends JpaRepository<ProbableAddressDebug, Long> {
    List<ProbableAddressDebug> findByLicensePlate(String licensePlate);

}
