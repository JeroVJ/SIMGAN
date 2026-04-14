package com.simgan.service;

import com.simgan.entity.Lote;
import com.simgan.entity.LoteParcelHistory;
import com.simgan.entity.Parcel;
import com.simgan.repository.LoteParcelHistoryRepository;
import com.simgan.repository.LoteRepository;
import com.simgan.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Checks daily whether any occupied parcel has expired its diasOcupacion.
 * When it has, the lote automatically advances to the next parcel in
 * rotation order (cycling back to 1 after the last position).
 *
 * Parcel status transitions:
 *   current → EN_USO  →  EN_DESCANSO
 *   next    → any     →  EN_USO
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RotationSchedulerService {

    private final LoteRepository loteRepository;
    private final ParcelRepository parcelRepository;
    private final LoteParcelHistoryRepository historyRepository;

    // Ejecuta una vez al iniciar el backend para no esperar la primera ventana del cron.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void runAtStartup() {
        checkAndAdvanceRotations();
    }
    

    /**
     * Runs every 6 hours by default. Override with
     *   rotation.schedule.cron=<expression> in application.properties.
     */
    @Scheduled(cron = "${rotation.schedule.cron:0 0 */6 * * *}")
    @Transactional
    public void checkAndAdvanceRotations() {
        LocalDate today = LocalDate.now();
        log.info("[RotationScheduler] Checking rotation advancement for {}", today);

        List<Lote> activeLotes = loteRepository.findByFechaSalidaIsNull();

        for (Lote lote : activeLotes) {
            if (lote.getCurrentParcel() == null) continue;

            Parcel currentParcel = lote.getCurrentParcel();
            if (currentParcel.getDiasOcupacion() == null
                    || currentParcel.getRotationOrder() == null) {
                continue; // parcel not configured for rotation
            }

            // Use the most-recent history entry; fechaSalida is pre-calculated on assign
            Optional<LoteParcelHistory> currentHistory =
                    historyRepository.findTopByLoteIdOrderByFechaIngresoDesc(lote.getId());
            if (currentHistory.isEmpty()) continue;  

            LoteParcelHistory hist = currentHistory.get();  

             //evitar rotar más de una vez el mismo día
            if (hist.getFechaIngreso() != null && hist.getFechaIngreso().equals(today)) {
                continue;
            }
            // If fechaSalida is missing (legacy/manual state), derive it from ingreso + DO.
            LocalDate dueDate = hist.getFechaSalida();
            if (dueDate == null
                    && hist.getFechaIngreso() != null
                    && currentParcel.getDiasOcupacion() != null) {
                dueDate = hist.getFechaIngreso().plusDays(Math.round(currentParcel.getDiasOcupacion()));
                hist.setFechaSalida(dueDate);
                historyRepository.save(hist);
                log.info("[RotationScheduler] Backfilled fechaSalida={} for lote '{}' in parcel '{}'.",
                        dueDate, lote.getName(), currentParcel.getName());
            }

            // Rotate only when due date exists and has been reached.
            if (dueDate == null || today.isBefore(dueDate)) {
                continue; // not yet expired or no planned rotation date
            }

            // If backend was down and rotation is overdue, backfill using the due date
            // instead of shifting history to "today".
            LocalDate effectiveTransitionDate = dueDate.isBefore(today) ? dueDate : today;

            log.info("[RotationScheduler] Lote '{}' rotation date {} reached in '{}'. Advancing.",
                    lote.getName(), dueDate, currentParcel.getName());

            advanceToNextParcel(lote, currentParcel, hist, effectiveTransitionDate);
        }
    }

    /**
     * Moves a lote from its current parcel to the next one in rotation order.
     * After last order → wraps around to order 1.
     */
    private void advanceToNextParcel(Lote lote, Parcel currentParcel,
                                      LoteParcelHistory openHistory, LocalDate transitionDate) {
        Long terrainId = currentParcel.getTerrain().getId();

        // All parcels of this terrain that participate in rotation, sorted by order
        List<Parcel> rotationParcels = parcelRepository
                .findByTerrainIdAndRotationOrderIsNotNullOrderByRotationOrder(terrainId);

        if (rotationParcels.size() < 2) {
            log.warn("[RotationScheduler] Terrain {} has fewer than 2 rotation parcels; skipping.", terrainId);
            return;
        }

        // Determine next parcel by position in sorted sequence.
        int currentIndex = -1;
        for (int i = 0; i < rotationParcels.size(); i++) {
            if (rotationParcels.get(i).getId().equals(currentParcel.getId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex < 0) {
            log.warn("[RotationScheduler] Current parcel {} not present in configured rotation for terrain {}. Skipping.",
                    currentParcel.getId(), terrainId);
            return;
        }

        int nextIndex = (currentIndex + 1) % rotationParcels.size();
        Parcel nextParcel = rotationParcels.get(nextIndex);
        Integer currentOrder = currentParcel.getRotationOrder();
        Integer nextOrder = nextParcel.getRotationOrder();

        // Safety: never assign a lote back to the parcel it just left
        if (nextParcel.getId().equals(currentParcel.getId())) {
            log.warn("[RotationScheduler] nextParcel is the same as currentParcel (order={}). Rotation misconfigured for terrain {}.",
                    currentOrder, terrainId);
            return;
        }

        // 1. Close history: keep the pre-calculated fechaSalida (already set)
        //    (the entry was set when the lote was first assigned to this parcel)
        // Nothing to change — fechaSalida is already the rotation date.
        historyRepository.save(openHistory); // no-op but explicit

        // 2. Current parcel: EN_USO → EN_DESCANSO
        currentParcel.setStatus(Parcel.ParcelStatus.EN_DESCANSO);
        parcelRepository.save(currentParcel);

        // 3. Next parcel: → EN_USO
        nextParcel.setStatus(Parcel.ParcelStatus.EN_USO);
        parcelRepository.save(nextParcel);

        // 4. Update lote's current parcel
        lote.setCurrentParcel(nextParcel);
        loteRepository.save(lote);

        // 5. Open new history entry with pre-calculated fechaSalida for next parcel
        LocalDate nextSalida = (nextParcel.getDiasOcupacion() != null)
            ? transitionDate.plusDays(Math.round(nextParcel.getDiasOcupacion()))
                : null;
        LoteParcelHistory newHistory = LoteParcelHistory.builder()
                .lote(lote)
                .parcel(nextParcel)
            .fechaIngreso(transitionDate)
                .fechaSalida(nextSalida)
                .build();
        historyRepository.save(newHistory);

        log.info("[RotationScheduler] Lote '{}' rotated: '{}' (order {}) → '{}' (order {}) next rotation={}",
                lote.getName(), currentParcel.getName(), currentOrder,
                nextParcel.getName(), nextOrder, nextSalida);
    }

    /**
     * Manually trigger rotation check (e.g., from a REST endpoint or tests).
     */
    @Transactional
    public void triggerNow() {
        checkAndAdvanceRotations();
    }
}
