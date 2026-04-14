package com.simgan.repository;

import com.simgan.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByParcelIdOrderByCreatedAtDesc(Long parcelId);

    @Query("SELECT a FROM Alert a WHERE a.parcel.terrain.id = :terrainId ORDER BY a.createdAt DESC")
    List<Alert> findByTerrainIdOrderByCreatedAtDesc(@Param("terrainId") Long terrainId);

    /** Alertas creadas en una fecha específica para una parcela */
    @Query("SELECT a FROM Alert a WHERE a.parcel.id = :parcelId AND CAST(a.createdAt AS date) = :date")
    List<Alert> findByParcelIdAndDate(@Param("parcelId") Long parcelId, @Param("date") LocalDate date);

    /**
     * Carga el Alert junto con toda la cadena de asociaciones lazy necesaria para
     * enviar el correo: Parcel → Terrain → Farm → Ganadero.
     * Se usa dentro del método @Async de EmailAlertService, donde la sesión JPA
     * original ya está cerrada y no se pueden navegar relaciones lazy sin re-fetch.
     */
    @Query("""
            SELECT a FROM Alert a
            JOIN FETCH a.parcel p
            JOIN FETCH p.terrain t
            JOIN FETCH t.farm f
            JOIN FETCH f.ganadero g
            WHERE a.id = :id
            """)
    Optional<Alert> findByIdWithFullGraph(@Param("id") Long id);
}
