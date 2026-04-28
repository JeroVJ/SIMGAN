package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ndvi_calibration_jobs", indexes = {
        @Index(name = "idx_ndvi_calib_job_terrain_status", columnList = "terrain_id, status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NdviCalibrationJob {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terrain_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Terrain terrain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.RUNNING;

    @Column(name = "weeks_total", nullable = false)
    @Builder.Default
    private Integer weeksTotal = 52;

    @Column(name = "weeks_completed", nullable = false)
    @Builder.Default
    private Integer weeksCompleted = 0;

    @Column(name = "scenes_processed", nullable = false)
    @Builder.Default
    private Integer scenesProcessed = 0;

    @Column(name = "range_start")
    private LocalDate rangeStart;

    @Column(name = "range_end")
    private LocalDate rangeEnd;

    @Column(name = "current_week_start")
    private LocalDate currentWeekStart;

    /** p25 / p75 thresholds derived once the job completes. */
    @Column(name = "threshold_low")
    private Double thresholdLow;

    @Column(name = "threshold_high")
    private Double thresholdHigh;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
