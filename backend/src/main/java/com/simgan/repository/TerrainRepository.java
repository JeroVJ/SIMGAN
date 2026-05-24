package com.simgan.repository;

import com.simgan.entity.Terrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
/**
 * Acceso a datos de {@link com.simgan.entity.Terrain} usando Spring Data JPA.
 *
 * Conceptos:
 * - Un terreno pertenece a una finca (farmId).
 * - El nombre del terreno es único dentro de una misma finca (restricción uk_terrain_farm_name).
 * - analysisScheduleDays/nextAnalysisDueDate se usan para programar análisis NDVI automáticos.
 */
public interface TerrainRepository extends JpaRepository<Terrain, Long> {
    List<Terrain> findByFarmId(Long farmId);
    boolean existsByFarmIdAndNameIgnoreCase(Long farmId, String name);
    boolean existsByFarmIdAndNameIgnoreCaseAndIdNot(Long farmId, String name, Long id);

    /**
     * Terrenos con programación habilitada (analysisScheduleDays != null) y cuyo próximo análisis
     * está vencido o vence en la fecha indicada.
     */
    List<Terrain> findByAnalysisScheduleDaysIsNotNullAndNextAnalysisDueDateLessThanEqual(LocalDate dueDate);

    /**
     * Variante de findById que carga "en caliente" la finca y su ganadero (evita LazyInitialization
     * cuando se necesita el grafo completo fuera de una transacción).
     */
    @Query("""
            SELECT t FROM Terrain t
            JOIN FETCH t.farm f
            JOIN FETCH f.ganadero g
            WHERE t.id = :id
            """)
    Optional<Terrain> findByIdWithFullGraph(@Param("id") Long id);
}
