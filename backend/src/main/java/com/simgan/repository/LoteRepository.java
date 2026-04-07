package com.simgan.repository;

import com.simgan.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {
    List<Lote> findByTerrainIdOrderByCreatedAtDesc(Long terrainId);
    List<Lote> findByTerrainIdAndFechaSalidaIsNullOrderByCreatedAtDesc(Long terrainId);
    List<Lote> findByCurrentParcelId(Long parcelId);
    List<Lote> findByFechaSalidaIsNull();
}
