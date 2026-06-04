package com.simgan.repository;

import com.simgan.entity.RotationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RotationHistoryRepository extends JpaRepository<RotationHistory, Long> {

    List<RotationHistory> findByParcelIdOrderByChangedAtDesc(Long parcelId);

    List<RotationHistory> findByParcelTerrainIdOrderByChangedAtDesc(Long terrainId);

    void deleteByParcelTerrainId(Long terrainId);
}
