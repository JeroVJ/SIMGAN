package com.simgan.repository;

import com.simgan.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByParcelIdOrderByCreatedAtDesc(Long parcelId);

    @Query("SELECT a FROM Alert a WHERE a.parcel.terrain.id = :terrainId ORDER BY a.createdAt DESC")
    List<Alert> findByTerrainIdOrderByCreatedAtDesc(@Param("terrainId") Long terrainId);

    /** Alertas creadas en una fecha específica para una parcela */
    @Query("SELECT a FROM Alert a WHERE a.parcel.id = :parcelId AND CAST(a.createdAt AS date) = :date")
    List<Alert> findByParcelIdAndDate(@Param("parcelId") Long parcelId, @Param("date") LocalDate date);
}
