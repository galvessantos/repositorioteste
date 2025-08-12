package com.montreal.msiav_bh.repository;

import com.montreal.msiav_bh.entity.VehicleCacheUniqueIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleCacheUniqueIndexRepository extends JpaRepository<VehicleCacheUniqueIndex, Long> {

    Optional<VehicleCacheUniqueIndex> findByVehicleCacheId(Long vehicleCacheId);

    Optional<VehicleCacheUniqueIndex> findByContratoHash(String contratoHash);

    Optional<VehicleCacheUniqueIndex> findByPlacaHash(String placaHash);

    Optional<VehicleCacheUniqueIndex> findByContratoPlacaHash(String contratoPlacaHash);

    @Query("SELECT v FROM VehicleCacheUniqueIndex v WHERE " +
           "(v.contratoHash = :contratoHash OR " +
           "v.placaHash = :placaHash OR " +
           "v.contratoPlacaHash = :contratoPlacaHash)")
    Optional<VehicleCacheUniqueIndex> findByAnyHash(@Param("contratoHash") String contratoHash,
                                                     @Param("placaHash") String placaHash,
                                                     @Param("contratoPlacaHash") String contratoPlacaHash);

    @Modifying
    @Query("DELETE FROM VehicleCacheUniqueIndex v WHERE v.vehicleCacheId = :vehicleCacheId")
    void deleteByVehicleCacheId(@Param("vehicleCacheId") Long vehicleCacheId);

    @Modifying
    @Query("DELETE FROM VehicleCacheUniqueIndex v WHERE v.vehicleCacheId IN :vehicleCacheIds")
    void deleteByVehicleCacheIds(@Param("vehicleCacheIds") Iterable<Long> vehicleCacheIds);
}