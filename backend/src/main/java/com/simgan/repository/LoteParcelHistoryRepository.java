package com.simgan.repository;

import com.simgan.entity.LoteParcelHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoteParcelHistoryRepository extends JpaRepository<LoteParcelHistory, Long> {
    List<LoteParcelHistory> findByLoteIdOrderByFechaIngresoDesc(Long loteId);
    /** Legacy: used when parcel has no diasOcupacion (fechaSalida intentionally null). */
    Optional<LoteParcelHistory> findByLoteIdAndFechaSalidaIsNull(Long loteId);
    /** Current assignment: the most recent history entry regardless of fechaSalida. */
    Optional<LoteParcelHistory> findTopByLoteIdOrderByFechaIngresoDesc(Long loteId);
}
