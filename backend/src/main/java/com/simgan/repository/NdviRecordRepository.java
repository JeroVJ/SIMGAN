package com.simgan.repository;

import com.simgan.entity.NdviRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NdviRecordRepository extends JpaRepository<NdviRecord, Long> {

    List<NdviRecord> findByParcelIdOrderByCaptureDate(Long parcelId);

    List<NdviRecord> findByTerrainIdOrderByCaptureDate(Long terrainId);

    List<NdviRecord> findByParcelIdAndCaptureDateBetweenOrderByCaptureDate(
            Long parcelId, LocalDate start, LocalDate end);

    List<NdviRecord> findByTerrainIdAndCaptureDateBetweenOrderByCaptureDate(
            Long terrainId, LocalDate start, LocalDate end);

    Optional<NdviRecord> findFirstByParcelIdOrderByCaptureDateDesc(Long parcelId);

    Optional<NdviRecord> findFirstByTerrainIdOrderByCaptureDateDesc(Long terrainId);

    @Query("SELECT n FROM NdviRecord n WHERE n.parcel.id = :parcelId ORDER BY n.captureDate DESC LIMIT :limit")
    List<NdviRecord> findLatestByParcelId(@Param("parcelId") Long parcelId, @Param("limit") int limit);

    @Query("SELECT AVG(n.meanNdvi) FROM NdviRecord n WHERE n.parcel.id = :parcelId")
    Optional<Double> findAvgNdviByParcelId(@Param("parcelId") Long parcelId);

    @Query("SELECT n FROM NdviRecord n WHERE n.terrain.id = :terrainId AND n.captureDate = :date")
    List<NdviRecord> findByTerrainIdAndDate(@Param("terrainId") Long terrainId, @Param("date") LocalDate date);

        Optional<NdviRecord> findByParcelIdAndCaptureDate(Long parcelId, LocalDate captureDate);

    boolean existsByParcelIdAndCaptureDate(Long parcelId, LocalDate captureDate);

    long countByTerrainId(Long terrainId);

    @Modifying
    @Query("DELETE FROM NdviRecord n WHERE n.terrain.id = :terrainId OR n.parcel.terrain.id = :terrainId")
    void deleteByTerrainOrParcelTerrainId(@Param("terrainId") Long terrainId);
}
