package com.simgan.repository;

import com.simgan.entity.Ganado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GanadoRepository extends JpaRepository<Ganado, Long> {
    List<Ganado> findByLoteIdOrderByNumeracion(Long loteId);
    long countByLoteId(Long loteId);
}
