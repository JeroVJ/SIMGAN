package com.simgan.repository;

import com.simgan.entity.Ganadero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GanaderoRepository extends JpaRepository<Ganadero, Long> {

    Optional<Ganadero> findByCorreo(String correo);

    Optional<Ganadero> findByIdDocumento(int idDocumento);

}