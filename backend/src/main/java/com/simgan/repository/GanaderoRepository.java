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

    /** Traverse Parcel → Terrain → Farm → Ganadero to get the owner's email */
    @Query("SELECT g.correo FROM Ganadero g " +
           "JOIN g.fincas f JOIN f.terrains t JOIN t.parcels p " +
           "WHERE p.id = :parcelId")
    Optional<String> findEmailByParcelId(@Param("parcelId") Long parcelId);
}