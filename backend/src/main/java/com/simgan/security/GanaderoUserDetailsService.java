package com.simgan.security;

import com.simgan.entity.Ganadero;
import com.simgan.repository.GanaderoRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GanaderoUserDetailsService implements UserDetailsService {

    private final GanaderoRepository ganaderoRepository;

    public GanaderoUserDetailsService(GanaderoRepository ganaderoRepository) {
        this.ganaderoRepository = ganaderoRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Ganadero ganadero = ganaderoRepository.findByCorreo(username)
                .orElseThrow(() -> new UsernameNotFoundException("Ganadero not found: " + username));

        return User.builder()
                .username(ganadero.getCorreo())
                .password(ganadero.getContrasena()) // Assuming password is encoded
                .roles("GANADERO")
                .build();
    }
}