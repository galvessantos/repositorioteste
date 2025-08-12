package com.montreal.msiav_bh.repository;

import com.montreal.msiav_bh.entity.QueryResult;
import com.montreal.msiav_bh.entity.VehicleDebug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface QueryResultRepository extends JpaRepository<QueryResult, Long> {

    Optional<QueryResult> findByVehicle(VehicleDebug vehicle);

}
