package com.simgan.repository;

import com.simgan.entity.NdviCalibrationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NdviCalibrationJobRepository extends JpaRepository<NdviCalibrationJob, Long> {

    Optional<NdviCalibrationJob> findFirstByTerrainIdAndStatus(Long terrainId, NdviCalibrationJob.Status status);

    Optional<NdviCalibrationJob> findFirstByTerrainIdOrderByStartedAtDesc(Long terrainId);

    @Query("""
            SELECT j FROM NdviCalibrationJob j
            JOIN FETCH j.terrain t
            JOIN FETCH t.farm f
            JOIN FETCH f.ganadero g
            WHERE j.id = :id
            """)
    Optional<NdviCalibrationJob> findByIdWithFullGraph(@Param("id") Long id);
}
