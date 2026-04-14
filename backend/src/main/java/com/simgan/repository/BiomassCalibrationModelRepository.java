package com.simgan.repository;

import com.simgan.entity.BiomassCalibrationModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BiomassCalibrationModelRepository extends JpaRepository<BiomassCalibrationModel, Long> {

    Optional<BiomassCalibrationModel> findByParcelId(Long parcelId);

    List<BiomassCalibrationModel> findByTerrainId(Long terrainId);

    void deleteByParcelId(Long parcelId);
}
