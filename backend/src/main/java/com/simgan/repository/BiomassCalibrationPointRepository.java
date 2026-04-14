package com.simgan.repository;

import com.simgan.entity.BiomassCalibrationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BiomassCalibrationPointRepository extends JpaRepository<BiomassCalibrationPoint, Long> {

    List<BiomassCalibrationPoint> findByParcelIdOrderByPointIndex(Long parcelId);

    List<BiomassCalibrationPoint> findByTerrainId(Long terrainId);

    void deleteByParcelId(Long parcelId);
}
