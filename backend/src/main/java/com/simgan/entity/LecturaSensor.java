package com.simgan.entity;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturaSensor {

    private Double valorHumedad;

    private LocalDateTime timestamp;

}