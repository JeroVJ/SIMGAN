package com.simgan.repository;

import com.simgan.entity.NdviAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NdviAlertRepository extends JpaRepository<NdviAlert, Long> {

    List<NdviAlert> findByParcelIdOrderByCreatedAtDesc(Long parcelId);

    List<NdviAlert> findByParcelTerrainIdOrderByCreatedAtDesc(Long terrainId);

    List<NdviAlert> findByParcelTerrainIdAndAcknowledgedFalseOrderByCreatedAtDesc(Long terrainId);

    long countByParcelTerrainIdAndAcknowledgedFalse(Long terrainId);
}
