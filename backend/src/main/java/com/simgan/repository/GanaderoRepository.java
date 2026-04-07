package com.simgan.repository;

import com.simgan.entity.Ganadero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GanaderoRepository extends JpaRepository<Ganadero, Long> {

    Optional<Ganadero> findByCorreo(String correo);

    Optional<Ganadero> findByIdDocumento(int idDocumento);

    @Query("""
            SELECT g.correo
            FROM Parcel p
            JOIN p.terrain t
            JOIN t.farm f
            JOIN f.ganadero g
            WHERE p.id = :parcelId
            """)
    Optional<String> findEmailByParcelId(@Param("parcelId") Long parcelId);

}