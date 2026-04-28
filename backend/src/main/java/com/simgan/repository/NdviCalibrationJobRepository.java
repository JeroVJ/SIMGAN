package com.simgan.repository;

import com.simgan.entity.NdviCalibrationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NdviCalibrationJobRepository extends JpaRepository<NdviCalibrationJob, Long> {

    Optional<NdviCalibrationJob> findFirstByTerrainIdAndStatus(Long terrainId, NdviCalibrationJob.Status status);

    Optional<NdviCalibrationJob> findFirstByTerrainIdOrderByStartedAtDesc(Long terrainId);
}
