package com.simgan.repository;

import com.simgan.entity.NdviCalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NdviCalibrationRepository extends JpaRepository<NdviCalibration, Long> {

    List<NdviCalibration> findByTerrainId(Long terrainId);

    List<NdviCalibration> findByTerrainIdAndCalibrationType(Long terrainId, String calibrationType);

    Optional<NdviCalibration> findByTerrainIdAndParcelIdIsNull(Long terrainId);

    Optional<NdviCalibration> findByTerrainIdAndParcelIdIsNullAndCalibrationType(Long terrainId, String calibrationType);

    Optional<NdviCalibration> findByParcelId(Long parcelId);

    Optional<NdviCalibration> findByParcelIdAndCalibrationType(Long parcelId, String calibrationType);

    boolean existsByTerrainId(Long terrainId);

    void deleteByTerrainId(Long terrainId);
}
