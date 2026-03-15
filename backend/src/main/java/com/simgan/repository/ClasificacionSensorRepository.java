package com.simgan.repository;

import com.simgan.entity.ClasificacionSensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClasificacionSensorRepository extends JpaRepository<ClasificacionSensor, Long> {
}
