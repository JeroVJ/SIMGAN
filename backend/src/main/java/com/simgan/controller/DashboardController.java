package com.simgan.controller;

import com.simgan.dto.DashboardDto;
import com.simgan.entity.Ganadero;
import com.simgan.service.DashboardService;
import com.simgan.service.GanaderoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final GanaderoService ganaderoService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardDto.SummaryResponse> getSummary(
            Authentication authentication,
            @RequestParam(required = false) Long farmId) {
        
        String email = authentication.getName();
        Ganadero ganadero = ganaderoService.buscarPorCorreo(email).orElseThrow();
        
        return ResponseEntity.ok(dashboardService.getDashboardSummary(ganadero.getId(), farmId));
    }
}
