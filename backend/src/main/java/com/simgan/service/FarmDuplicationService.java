package com.simgan.service;

import com.simgan.entity.*;
import com.simgan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep-copies a farm and everything under it into a brand-new farm owned by the
 * same ganadero, remapping every foreign key:
 *
 *   Farm → Terrains → Parcels
 *        → NDVI calibrations (terrain & parcel level)
 *        → calibration jobs → terrain NDVI records
 *        → per-parcel NDVI records → rotation history
 *        → Lotes → Ganado + lote-parcel history
 *
 * IoT sensors and alerts are intentionally NOT copied (hardware bindings /
 * generated history, not useful for an NDVI/rotation test clone).
 *
 * Intended for testing: clone a real farm to experiment with rotations and
 * low-NDVI scenarios without touching production data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FarmDuplicationService {

    private final FarmRepository farmRepository;
    private final TerrainRepository terrainRepository;
    private final ParcelRepository parcelRepository;
    private final NdviCalibrationRepository calibrationRepository;
    private final NdviCalibrationJobRepository jobRepository;
    private final NdviTerrainRecordRepository terrainRecordRepository;
    private final NdviRecordRepository ndviRecordRepository;
    private final RotationHistoryRepository rotationHistoryRepository;
    private final LoteRepository loteRepository;
    private final GanadoRepository ganadoRepository;
    private final LoteParcelHistoryRepository loteParcelHistoryRepository;

    @Transactional
    public Map<String, Object> duplicate(Long farmId, String email) {
        Farm oldFarm = farmRepository.findById(farmId)
                .orElseThrow(() -> new RuntimeException("Finca no encontrada: " + farmId));

        // Only the owner may duplicate their own farm.
        if (oldFarm.getGanadero() == null
                || oldFarm.getGanadero().getCorreo() == null
                || !oldFarm.getGanadero().getCorreo().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("No tienes acceso a esta finca");
        }

        // 1) Farm
        Farm newFarm = new Farm();
        newFarm.setName(oldFarm.getName() + " (copia)");
        newFarm.setGanadero(oldFarm.getGanadero());
        newFarm.setDepartment(oldFarm.getDepartment());
        newFarm.setMunicipality(oldFarm.getMunicipality());
        newFarm.setCenterLat(oldFarm.getCenterLat());
        newFarm.setCenterLng(oldFarm.getCenterLng());
        newFarm.setIsHomogeneous(oldFarm.getIsHomogeneous());
        newFarm.setSoilType(oldFarm.getSoilType());
        newFarm.setPastureType(oldFarm.getPastureType());
        newFarm.setIotEnabled(oldFarm.getIotEnabled());
        newFarm = farmRepository.save(newFarm);

        Map<Long, Terrain> terrainMap = new HashMap<>();
        Map<Long, Parcel> parcelMap = new HashMap<>();
        Map<Long, NdviCalibrationJob> jobMap = new HashMap<>();
        int parcelCount = 0;
        int loteCount = 0;

        List<Terrain> terrains = terrainRepository.findByFarmId(farmId);

        // 2) Terrains + parcels (built first so the maps are complete before
        //    anything that references parcels across the whole farm).
        for (Terrain ot : terrains) {
            Terrain nt = new Terrain();
            nt.setName(ot.getName());
            nt.setGeoJson(ot.getGeoJson());
            nt.setAreaSqMeters(ot.getAreaSqMeters());
            nt.setAreaHectares(ot.getAreaHectares());
            nt.setAnalysisScheduleDays(ot.getAnalysisScheduleDays());
            nt.setNextAnalysisDueDate(ot.getNextAnalysisDueDate());
            nt.setFarm(newFarm);
            nt = terrainRepository.save(nt);
            terrainMap.put(ot.getId(), nt);

            for (Parcel op : parcelRepository.findByTerrainId(ot.getId())) {
                Parcel np = new Parcel();
                np.setName(op.getName());
                np.setGeoJson(op.getGeoJson());
                np.setAreaSqMeters(op.getAreaSqMeters());
                np.setAreaHectares(op.getAreaHectares());
                np.setSoilType(op.getSoilType());
                np.setPastureType(op.getPastureType());
                np.setDiasOcupacion(op.getDiasOcupacion());
                np.setDiasDescanso(op.getDiasDescanso());
                np.setRotationOrder(op.getRotationOrder());
                np.setStatus(op.getStatus());
                np.setMonitoringEnabled(op.getMonitoringEnabled());
                np.setTerrain(nt);
                np = parcelRepository.save(np);
                parcelMap.put(op.getId(), np);
                parcelCount++;
            }
        }

        // 3) Per-terrain: calibration jobs, terrain NDVI records, calibrations,
        //    per-parcel NDVI records.
        for (Terrain ot : terrains) {
            Terrain nt = terrainMap.get(ot.getId());

            for (NdviCalibrationJob oj : jobRepository.findByTerrainId(ot.getId())) {
                NdviCalibrationJob nj = new NdviCalibrationJob();
                nj.setTerrain(nt);
                nj.setStatus(oj.getStatus());
                nj.setWeeksTotal(oj.getWeeksTotal());
                nj.setWeeksCompleted(oj.getWeeksCompleted());
                nj.setScenesProcessed(oj.getScenesProcessed());
                nj.setRangeStart(oj.getRangeStart());
                nj.setRangeEnd(oj.getRangeEnd());
                nj.setCurrentWeekStart(oj.getCurrentWeekStart());
                nj.setThresholdLow(oj.getThresholdLow());
                nj.setThresholdHigh(oj.getThresholdHigh());
                nj.setErrorMessage(oj.getErrorMessage());
                nj.setStartedAt(oj.getStartedAt());
                nj.setFinishedAt(oj.getFinishedAt());
                nj = jobRepository.save(nj);
                jobMap.put(oj.getId(), nj);
            }

            for (NdviTerrainRecord otr : terrainRecordRepository.findByTerrainIdOrderByCaptureDate(ot.getId())) {
                NdviTerrainRecord ntr = new NdviTerrainRecord();
                ntr.setTerrain(nt);
                ntr.setJob(otr.getJob() != null ? jobMap.get(otr.getJob().getId()) : null);
                ntr.setCaptureDate(otr.getCaptureDate());
                ntr.setMeanNdvi(otr.getMeanNdvi());
                ntr.setMinNdvi(otr.getMinNdvi());
                ntr.setMaxNdvi(otr.getMaxNdvi());
                ntr.setStdNdvi(otr.getStdNdvi());
                ntr.setMedianNdvi(otr.getMedianNdvi());
                ntr.setPixelCount(otr.getPixelCount());
                ntr.setVegetationCoverPercent(otr.getVegetationCoverPercent());
                ntr.setSceneId(otr.getSceneId());
                ntr.setCloudCoverPercent(otr.getCloudCoverPercent());
                ntr.setSource(otr.getSource());
                terrainRecordRepository.save(ntr);
            }

            for (NdviCalibration oc : calibrationRepository.findByTerrainId(ot.getId())) {
                NdviCalibration nc = new NdviCalibration();
                nc.setTerrain(nt);
                nc.setParcel(oc.getParcel() != null ? parcelMap.get(oc.getParcel().getId()) : null);
                nc.setCalibrationDate(oc.getCalibrationDate());
                nc.setCalibrationType(oc.getCalibrationType());
                nc.setReferenceNdvi(oc.getReferenceNdvi());
                nc.setPastureType(oc.getPastureType());
                nc.setSource(oc.getSource());
                nc.setSceneId(oc.getSceneId());
                nc.setCloudCoverPercent(oc.getCloudCoverPercent());
                nc.setPixelCount(oc.getPixelCount());
                calibrationRepository.save(nc);
            }

            for (NdviRecord onr : ndviRecordRepository.findByTerrainIdOrderByCaptureDate(ot.getId())) {
                NdviRecord nnr = new NdviRecord();
                nnr.setTerrain(nt);
                nnr.setParcel(onr.getParcel() != null ? parcelMap.get(onr.getParcel().getId()) : null);
                nnr.setCaptureDate(onr.getCaptureDate());
                nnr.setMeanNdvi(onr.getMeanNdvi());
                nnr.setMinNdvi(onr.getMinNdvi());
                nnr.setMaxNdvi(onr.getMaxNdvi());
                nnr.setStdNdvi(onr.getStdNdvi());
                nnr.setMedianNdvi(onr.getMedianNdvi());
                nnr.setPixelCount(onr.getPixelCount());
                nnr.setBiomassKgPerHa(onr.getBiomassKgPerHa());
                nnr.setVegetationCoverPercent(onr.getVegetationCoverPercent());
                nnr.setPlanetSceneId(onr.getPlanetSceneId());
                nnr.setCloudCoverPercent(onr.getCloudCoverPercent());
                nnr.setSource(onr.getSource());
                ndviRecordRepository.save(nnr);
            }
        }

        // 4) Rotation history (per parcel)
        for (Map.Entry<Long, Parcel> e : parcelMap.entrySet()) {
            for (RotationHistory orh : rotationHistoryRepository.findByParcelIdOrderByChangedAtDesc(e.getKey())) {
                RotationHistory nrh = new RotationHistory();
                nrh.setParcel(e.getValue());
                nrh.setPreviousStatus(orh.getPreviousStatus());
                nrh.setNewStatus(orh.getNewStatus());
                nrh.setNdviAtChange(orh.getNdviAtChange());
                nrh.setBiomassAtChange(orh.getBiomassAtChange());
                nrh.setNote(orh.getNote());
                rotationHistoryRepository.save(nrh);
            }
        }

        // 5) Lotes + ganado + lote-parcel history
        for (Terrain ot : terrains) {
            Terrain nt = terrainMap.get(ot.getId());
            for (Lote ol : loteRepository.findByTerrainIdOrderByCreatedAtDesc(ot.getId())) {
                Lote nl = new Lote();
                nl.setName(ol.getName());
                nl.setFechaIngreso(ol.getFechaIngreso());
                nl.setFechaSalida(ol.getFechaSalida());
                nl.setTerrain(nt);
                nl.setCurrentParcel(ol.getCurrentParcel() != null ? parcelMap.get(ol.getCurrentParcel().getId()) : null);
                nl = loteRepository.save(nl);
                loteCount++;

                for (Ganado og : ganadoRepository.findByLoteIdOrderByNumeracion(ol.getId())) {
                    Ganado ng = new Ganado();
                    ng.setNumeracion(og.getNumeracion());
                    ng.setTipo(og.getTipo());
                    ng.setPesoInicial(og.getPesoInicial());
                    ng.setPesoActual(og.getPesoActual());
                    ng.setLote(nl);
                    ganadoRepository.save(ng);
                }

                for (LoteParcelHistory olph : loteParcelHistoryRepository.findByLoteIdOrderByFechaIngresoDesc(ol.getId())) {
                    LoteParcelHistory nlph = new LoteParcelHistory();
                    nlph.setLote(nl);
                    nlph.setParcel(olph.getParcel() != null ? parcelMap.get(olph.getParcel().getId()) : null);
                    nlph.setFechaIngreso(olph.getFechaIngreso());
                    nlph.setFechaSalida(olph.getFechaSalida());
                    loteParcelHistoryRepository.save(nlph);
                }
            }
        }

        log.info("Finca {} duplicada -> nueva finca {} ({} terrenos, {} potreros, {} lotes)",
                farmId, newFarm.getId(), terrains.size(), parcelCount, loteCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newFarmId", newFarm.getId());
        result.put("name", newFarm.getName());
        result.put("terrainsCopied", terrains.size());
        result.put("parcelsCopied", parcelCount);
        result.put("lotesCopied", loteCount);
        return result;
    }
}
