package com.simgan.service;

import com.simgan.dto.DashboardDto;
import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FarmRepository farmRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final AlertRepository alertRepository;
    private final GanaderoRepository ganaderoRepository;

    public DashboardDto.SummaryResponse getDashboardSummary(Long ganaderoId, Long farmId) {
        List<Farm> farms = farmRepository.findByGanaderoId(ganaderoId);
        
        List<DashboardDto.FarmInfo> farmInfos = farms.stream()
                .map(f -> DashboardDto.FarmInfo.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .build())
                .collect(Collectors.toList());

        // If no farmId is provided, we can either aggregate all or pick the first one.
        // The user wants to filter by farm, so if no farmId, we'll return empty or default.
        if (farmId == null && !farms.isEmpty()) {
            farmId = farms.get(0).getId();
        }

        if (farmId == null) {
            return DashboardDto.SummaryResponse.builder()
                    .farms(farmInfos)
                    .build();
        }

        final Long targetFarmId = farmId;
        Farm selectedFarm = farms.stream()
                .filter(f -> f.getId().equals(targetFarmId))
                .findFirst()
                .orElse(null);

        if (selectedFarm == null) {
            return DashboardDto.SummaryResponse.builder()
                    .farms(farmInfos)
                    .build();
        }

        List<Terrain> terrains = selectedFarm.getTerrains();
        List<Parcel> allParcels = terrains.stream()
                .flatMap(t -> t.getParcels().stream())
                .collect(Collectors.toList());

        // Metrics
        double gananciaTotal = 0.0;
        int totalAnimales = 0;
        int potrerosDisponibles = 0;
        int potrerosOcupados = 0;
        int potrerosEnDescanso = 0;
        int potrerosEnAlerta = 0;
        int animalesAfectados = 0;

        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        int totalRotaciones24h = 0;

        for (Terrain terrain : terrains) {
            for (Lote lote : terrain.getLotes()) {
                totalAnimales += lote.getCabezas();
                for (Ganado g : lote.getGanados()) {
                    gananciaTotal += g.getGananciaPeso();
                }
                
                // Count rotaciones in last 24h
                for (LoteParcelHistory history : lote.getParcelHistory()) {
                    if (history.getFechaIngreso() != null) {
                        LocalDateTime ingresoDateTime = history.getFechaIngreso().atStartOfDay();
                        if (ingresoDateTime.isAfter(last24h)) {
                            totalRotaciones24h++;
                        }
                    }
                }

                // Check for affected animals
                if (lote.getCurrentParcel() != null) {
                    Parcel current = lote.getCurrentParcel();
                    boolean hasAlert = !current.getAlertas().isEmpty();
                    if (hasAlert) {
                        animalesAfectados += lote.getCabezas();
                    }
                }
            }
        }

        for (Parcel p : allParcels) {
            switch (p.getStatus()) {
                case DISPONIBLE -> potrerosDisponibles++;
                case EN_USO -> potrerosOcupados++;
                case EN_DESCANSO -> potrerosEnDescanso++;
            }
            if (!p.getAlertas().isEmpty()) {
                potrerosEnAlerta++;
            }
        }

        double densidad = allParcels.isEmpty() ? 0 : (double) totalAnimales / allParcels.size();

        return DashboardDto.SummaryResponse.builder()
                .gananciaTotalEstimada(gananciaTotal)
                .totalPotreros(allParcels.size())
                .potrerosDisponibles(potrerosDisponibles)
                .potrerosOcupados(potrerosOcupados)
                .potrerosEnDescanso(potrerosEnDescanso)
                .totalRotacionesUltima24h(totalRotaciones24h)
                .totalAnimales(totalAnimales)
                .densidadAnimal(densidad)
                .animalesAfectados(animalesAfectados)
                .potrerosEnAlerta(potrerosEnAlerta)
                .farms(farmInfos)
                .build();
    }
}
