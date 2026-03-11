package com.simgan.repository;

import com.simgan.entity.LoteParcelHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoteParcelHistoryRepository extends JpaRepository<LoteParcelHistory, Long> {
    List<LoteParcelHistory> findByLoteIdOrderByFechaIngresoDesc(Long loteId);
    Optional<LoteParcelHistory> findByLoteIdAndFechaSalidaIsNull(Long loteId);
}
