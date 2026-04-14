package com.simgan.service;



import com.simgan.entity.Ganadero;
import com.simgan.repository.GanaderoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GanaderoService {

    @Autowired
    private GanaderoRepository ganaderoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Ganadero guardarGanadero(Ganadero ganadero) {
        ganadero.setContrasena(passwordEncoder.encode(ganadero.getContrasena()));
        return ganaderoRepository.save(ganadero);
    }

    public List<Ganadero> listarGanaderos() {
        return ganaderoRepository.findAll();
    }

    public Optional<Ganadero> buscarPorId(Long id) {
        return ganaderoRepository.findById(id);
    }

    public void eliminarGanadero(Long id) {
        ganaderoRepository.deleteById(id);
    }

    public Optional<Ganadero> buscarPorCorreo(String correo) {
        return ganaderoRepository.findByCorreo(correo);
    }

    public Optional<Ganadero> buscarPorIdDocumento(int idDocumento) {
        return ganaderoRepository.findByIdDocumento(idDocumento);
    }  


     public void actualizarContrasena(Ganadero ganadero, String nuevaContrasena) {
        ganadero.setContrasena(passwordEncoder.encode(nuevaContrasena));
        ganaderoRepository.save(ganadero);
    }
}