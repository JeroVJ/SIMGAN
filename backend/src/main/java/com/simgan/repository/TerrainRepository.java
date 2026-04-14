package com.simgan.repository;

import com.simgan.entity.Terrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TerrainRepository extends JpaRepository<Terrain, Long> {
    List<Terrain> findByFarmId(Long farmId);
    boolean existsByFarmIdAndNameIgnoreCase(Long farmId, String name);
    boolean existsByFarmIdAndNameIgnoreCaseAndIdNot(Long farmId, String name, Long id);

    List<Terrain> findByAnalysisScheduleDaysIsNotNullAndNextAnalysisDueDateLessThanEqual(LocalDate dueDate);
}
