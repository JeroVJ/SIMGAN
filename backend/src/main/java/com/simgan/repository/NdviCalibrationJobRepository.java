package com.simgan.repository;

import com.simgan.entity.NdviCalibrationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NdviCalibrationJobRepository extends JpaRepository<NdviCalibrationJob, Long> {

    List<NdviCalibrationJob> findByTerrainId(Long terrainId);

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

    void deleteByTerrainId(Long terrainId);
}
