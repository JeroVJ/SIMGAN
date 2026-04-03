package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ganaderos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Ganadero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreCompleto;

    private String apellidoCompleto;

    private String correo;

    private String contrasena;

    private String tipoDocumento;

    private int idDocumento;

    @OneToMany(mappedBy = "ganadero", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Farm> fincas = new ArrayList<>();

}