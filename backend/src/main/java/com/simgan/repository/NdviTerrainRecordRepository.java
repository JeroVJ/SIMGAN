package com.simgan.repository;

import com.simgan.entity.NdviTerrainRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NdviTerrainRecordRepository extends JpaRepository<NdviTerrainRecord, Long> {

    List<NdviTerrainRecord> findByJobIdOrderByCaptureDate(Long jobId);

    List<NdviTerrainRecord> findByTerrainIdOrderByCaptureDate(Long terrainId);

    boolean existsByTerrainIdAndCaptureDate(Long terrainId, LocalDate captureDate);

    List<NdviTerrainRecord> findByTerrainIdAndCaptureDateBetweenOrderByCaptureDate(
            Long terrainId, LocalDate start, LocalDate end);

    Optional<NdviTerrainRecord> findFirstByTerrainIdAndCaptureDate(Long terrainId, LocalDate captureDate);

    /** Escena más reciente con sceneId, usada como referencia para la biomasa tras la auto-calibración. */
    Optional<NdviTerrainRecord> findFirstByTerrainIdAndSceneIdIsNotNullOrderByCaptureDateDesc(Long terrainId);

    /** Escenas disponibles (con sceneId) para elegir como referencia, de más reciente a más antigua. */
    List<NdviTerrainRecord> findByTerrainIdAndSceneIdIsNotNullOrderByCaptureDateDesc(Long terrainId);

    /** Escena concreta elegida por el usuario. */
    Optional<NdviTerrainRecord> findFirstByTerrainIdAndSceneId(Long terrainId, String sceneId);

    void deleteByTerrainId(Long terrainId);
}
