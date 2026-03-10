package com.simgan.controller;



import com.simgan.entity.Ganadero;
import com.simgan.service.GanaderoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/ganaderos")
public class GanaderoController {

    @Autowired
    private GanaderoService ganaderoService;

    @PostMapping
    public Ganadero crearGanadero(@RequestBody Ganadero ganadero) {
        return ganaderoService.guardarGanadero(ganadero);
    }

    @GetMapping
    public List<Ganadero> listarGanaderos() {
        return ganaderoService.listarGanaderos();
    }

    @GetMapping("/{id}")
    public Optional<Ganadero> obtenerGanadero(@PathVariable Long id) {
        return ganaderoService.buscarPorId(id);
    }

    @DeleteMapping("/{id}")
    public void eliminarGanadero(@PathVariable Long id) {
        ganaderoService.eliminarGanadero(id);
    }

    @GetMapping("/correo/{correo}")
    public Optional<Ganadero> buscarPorCorreo(@PathVariable String correo) {
        return ganaderoService.buscarPorCorreo(correo);
    }

    @GetMapping("/documento/{idDocumento}")
    public Optional<Ganadero> buscarPorDocumento(@PathVariable int idDocumento) {
        return ganaderoService.buscarPorIdDocumento(idDocumento);
    }
}
