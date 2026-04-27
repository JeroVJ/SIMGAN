package com.simgan.repository;

import com.simgan.entity.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    List<Parcel> findByTerrainId(Long terrainId);
    List<Parcel> findByTerrainIdAndRotationOrderIsNotNullOrderByRotationOrder(Long terrainId);
    List<Parcel> findByTerrainIdAndStatus(Long terrainId, Parcel.ParcelStatus status);
    boolean existsByTerrainIdAndNameIgnoreCase(Long terrainId, String name);
    boolean existsByTerrainIdAndNameIgnoreCaseAndIdNot(Long terrainId, String name, Long id);
}
