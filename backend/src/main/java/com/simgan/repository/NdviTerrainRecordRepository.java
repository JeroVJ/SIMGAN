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

    void deleteByTerrainId(Long terrainId);
}
