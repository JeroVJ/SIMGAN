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
public interface TerrainRepository extends JpaRepository<Terrain, Long> {
    List<Terrain> findByFarmId(Long farmId);
    boolean existsByFarmIdAndNameIgnoreCase(Long farmId, String name);
    boolean existsByFarmIdAndNameIgnoreCaseAndIdNot(Long farmId, String name, Long id);

    List<Terrain> findByAnalysisScheduleDaysIsNotNullAndNextAnalysisDueDateLessThanEqual(LocalDate dueDate);

    @Query("""
            SELECT t FROM Terrain t
            JOIN FETCH t.farm f
            JOIN FETCH f.ganadero g
            WHERE t.id = :id
            """)
    Optional<Terrain> findByIdWithFullGraph(@Param("id") Long id);
}
