package com.simgan.repository;

import com.simgan.entity.NdviAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NdviAlertRepository extends JpaRepository<NdviAlert, Long> {

    List<NdviAlert> findByParcelIdOrderByCreatedAtDesc(Long parcelId);

    List<NdviAlert> findByParcelTerrainIdOrderByCreatedAtDesc(Long terrainId);

    List<NdviAlert> findByParcelTerrainIdAndAcknowledgedFalseOrderByCreatedAtDesc(Long terrainId);

    Optional<NdviAlert> findFirstByParcelIdAndAlertTypeAndAcknowledgedFalseOrderByCreatedAtDesc(
            Long parcelId,
            NdviAlert.AlertType alertType);

    long countByParcelTerrainIdAndAcknowledgedFalse(Long terrainId);
}
