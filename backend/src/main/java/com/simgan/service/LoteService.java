package com.simgan.service;

import com.simgan.dto.LoteDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoteService {

    private final LoteRepository loteRepository;
    private final GanadoRepository ganadoRepository;
    private final LoteParcelHistoryRepository parcelHistoryRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;

    // ===== LOTES =====

    public List<LoteDto.LoteResponse> getLotesByTerrain(Long terrainId) {
        return loteRepository.findByTerrainIdOrderByCreatedAtDesc(terrainId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<LoteDto.LoteResponse> getActiveLotesByTerrain(Long terrainId) {
        return loteRepository.findByTerrainIdAndFechaSalidaIsNullOrderByCreatedAtDesc(terrainId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public LoteDto.LoteResponse getLote(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado: " + loteId));
        return toResponse(lote);
    }

    @Transactional
    public LoteDto.LoteResponse createLote(LoteDto.CreateLoteRequest req) {
        Terrain terrain = terrainRepository.findById(req.getTerrainId())
                .orElseThrow(() -> new RuntimeException("Terreno no encontrado"));

        Lote lote = Lote.builder()
                .name(req.getName())
                .fechaIngreso(LocalDate.parse(req.getFechaIngreso()))
                .terrain(terrain)
                .build();

        lote = loteRepository.save(lote);
        log.info("Lote creado: {} en terreno {}", lote.getName(), terrain.getName());
        return toResponse(lote);
    }

    @Transactional
    public LoteDto.LoteResponse closeLote(Long loteId, LoteDto.CloseLoteRequest req) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado"));

        // Unassign from current parcel
        if (lote.getCurrentParcel() != null) {
            unassignFromCurrentParcel(lote);
        }

        lote.setFechaSalida(LocalDate.parse(req.getFechaSalida()));
        lote = loteRepository.save(lote);
        log.info("Lote {} cerrado. Fecha salida: {}", lote.getName(), lote.getFechaSalida());
        return toResponse(lote);
    }

    @Transactional
    public void deleteLote(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado"));

        if (lote.getCurrentParcel() != null) {
            Parcel parcel = lote.getCurrentParcel();
            parcel.setStatus(Parcel.ParcelStatus.DISPONIBLE);
            parcelRepository.save(parcel);
        }

        loteRepository.delete(lote);
        log.info("Lote {} eliminado", loteId);
    }

    // ===== ASIGNACIÓN DE PARCELA =====

    @Transactional
    public LoteDto.LoteResponse assignParcel(Long loteId, LoteDto.AssignParcelRequest req) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado"));

        Parcel newParcel = parcelRepository.findById(req.getParcelId())
                .orElseThrow(() -> new RuntimeException("Parcela no encontrada"));

        // Check parcel belongs to same terrain
        if (!newParcel.getTerrain().getId().equals(lote.getTerrain().getId())) {
            throw new RuntimeException("La parcela no pertenece al mismo terreno del lote");
        }

        // Check parcel is available
        if (newParcel.getStatus() == Parcel.ParcelStatus.EN_USO) {
            // Check if it's in use by another lote
            List<Lote> occupying = loteRepository.findByCurrentParcelId(newParcel.getId());
            if (!occupying.isEmpty() && occupying.stream().noneMatch(l -> l.getId().equals(loteId))) {
                throw new RuntimeException("La parcela ya está ocupada por otro lote: " + occupying.get(0).getName());
            }
        }

        // Unassign from previous parcel
        if (lote.getCurrentParcel() != null) {
            unassignFromCurrentParcel(lote);
        }

        // Assign to new parcel
        lote.setCurrentParcel(newParcel);
        newParcel.setStatus(Parcel.ParcelStatus.EN_USO);
        parcelRepository.save(newParcel);

        // Create history entry
        LoteParcelHistory history = LoteParcelHistory.builder()
                .lote(lote)
                .parcel(newParcel)
                .fechaIngreso(LocalDate.now())
                .build();
        parcelHistoryRepository.save(history);

        lote = loteRepository.save(lote);
        log.info("Lote {} asignado a parcela {}", lote.getName(), newParcel.getName());
        return toResponse(lote);
    }

    @Transactional
    public LoteDto.LoteResponse unassignParcel(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado"));

        if (lote.getCurrentParcel() == null) {
            throw new RuntimeException("El lote no tiene parcela asignada");
        }

        unassignFromCurrentParcel(lote);
        lote = loteRepository.save(lote);
        return toResponse(lote);
    }

    private void unassignFromCurrentParcel(Lote lote) {
        Parcel prev = lote.getCurrentParcel();
        prev.setStatus(Parcel.ParcelStatus.EN_DESCANSO);
        parcelRepository.save(prev);

        // Close history entry
        parcelHistoryRepository.findByLoteIdAndFechaSalidaIsNull(lote.getId())
                .ifPresent(h -> {
                    h.setFechaSalida(LocalDate.now());
                    parcelHistoryRepository.save(h);
                });

        lote.setCurrentParcel(null);
    }

    // ===== GANADO =====

    public List<LoteDto.GanadoResponse> getGanadoByLote(Long loteId) {
        return ganadoRepository.findByLoteIdOrderByNumeracion(loteId)
                .stream().map(this::toGanadoResponse).collect(Collectors.toList());
    }

    @Transactional
    public LoteDto.GanadoResponse addGanado(LoteDto.CreateGanadoRequest req) {
        Lote lote = loteRepository.findById(req.getLoteId())
                .orElseThrow(() -> new RuntimeException("Lote no encontrado"));

        Double pesoActual = req.getPesoActual() != null ? req.getPesoActual() : req.getPesoInicial();

        Ganado ganado = Ganado.builder()
                .numeracion(req.getNumeracion())
                .tipo(req.getTipo())
                .pesoInicial(req.getPesoInicial())
                .pesoActual(pesoActual)
                .lote(lote)
                .build();

        ganado = ganadoRepository.save(ganado);
        log.info("Ganado {} ({}) agregado al lote {}", ganado.getNumeracion(), ganado.getTipo(), lote.getName());
        return toGanadoResponse(ganado);
    }

    @Transactional
    public List<LoteDto.GanadoResponse> addGanadoBatch(Long loteId, List<LoteDto.CreateGanadoRequest> requests) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new RuntimeException("Lote no encontrado"));

        List<LoteDto.GanadoResponse> results = new ArrayList<>();
        for (LoteDto.CreateGanadoRequest req : requests) {
            req.setLoteId(loteId);
            results.add(addGanado(req));
        }
        return results;
    }

    @Transactional
    public LoteDto.GanadoResponse updateGanado(Long ganadoId, LoteDto.UpdateGanadoRequest req) {
        Ganado ganado = ganadoRepository.findById(ganadoId)
                .orElseThrow(() -> new RuntimeException("Ganado no encontrado"));

        if (req.getNumeracion() != null) ganado.setNumeracion(req.getNumeracion());
        if (req.getTipo() != null) ganado.setTipo(req.getTipo());
        if (req.getPesoActual() != null) ganado.setPesoActual(req.getPesoActual());

        ganado = ganadoRepository.save(ganado);
        log.info("Ganado {} actualizado. Peso actual: {} kg", ganado.getNumeracion(), ganado.getPesoActual());
        return toGanadoResponse(ganado);
    }

    @Transactional
    public void deleteGanado(Long ganadoId) {
        ganadoRepository.deleteById(ganadoId);
    }

    // ===== MAPPERS =====

    private LoteDto.LoteResponse toResponse(Lote lote) {
        List<Ganado> ganados = ganadoRepository.findByLoteIdOrderByNumeracion(lote.getId());
        List<LoteParcelHistory> history = parcelHistoryRepository.findByLoteIdOrderByFechaIngresoDesc(lote.getId());

        Double pesoPromedio = ganados.isEmpty() ? null :
                ganados.stream().mapToDouble(Ganado::getPesoActual).average().orElse(0);
        Double gananciaPromedio = ganados.isEmpty() ? null :
                ganados.stream().mapToDouble(Ganado::getGananciaPeso).average().orElse(0);

        return LoteDto.LoteResponse.builder()
                .id(lote.getId())
                .name(lote.getName())
                .fechaIngreso(lote.getFechaIngreso() != null ? lote.getFechaIngreso().toString() : null)
                .fechaSalida(lote.getFechaSalida() != null ? lote.getFechaSalida().toString() : null)
                .terrainId(lote.getTerrain().getId())
                .terrainName(lote.getTerrain().getName())
                .currentParcelId(lote.getCurrentParcel() != null ? lote.getCurrentParcel().getId() : null)
                .currentParcelName(lote.getCurrentParcel() != null ? lote.getCurrentParcel().getName() : null)
                .cabezas(ganados.size())
                .pesoPromedioActual(pesoPromedio != null ? Math.round(pesoPromedio * 10.0) / 10.0 : null)
                .gananciaPromedioKg(gananciaPromedio != null ? Math.round(gananciaPromedio * 10.0) / 10.0 : null)
                .ganados(ganados.stream().map(this::toGanadoResponse).collect(Collectors.toList()))
                .parcelHistory(history.stream().map(this::toHistoryResponse).collect(Collectors.toList()))
                .createdAt(lote.getCreatedAt() != null ? lote.getCreatedAt().toString() : null)
                .build();
    }

    private LoteDto.GanadoResponse toGanadoResponse(Ganado g) {
        return LoteDto.GanadoResponse.builder()
                .id(g.getId())
                .numeracion(g.getNumeracion())
                .tipo(g.getTipo())
                .pesoInicial(g.getPesoInicial())
                .pesoActual(g.getPesoActual())
                .gananciaPeso(g.getGananciaPeso())
                .loteId(g.getLote().getId())
                .createdAt(g.getCreatedAt() != null ? g.getCreatedAt().toString() : null)
                .build();
    }

    private LoteDto.ParcelHistoryResponse toHistoryResponse(LoteParcelHistory h) {
        return LoteDto.ParcelHistoryResponse.builder()
                .id(h.getId())
                .parcelId(h.getParcel().getId())
                .parcelName(h.getParcel().getName())
                .fechaIngreso(h.getFechaIngreso() != null ? h.getFechaIngreso().toString() : null)
                .fechaSalida(h.getFechaSalida() != null ? h.getFechaSalida().toString() : null)
                .build();
    }
}
