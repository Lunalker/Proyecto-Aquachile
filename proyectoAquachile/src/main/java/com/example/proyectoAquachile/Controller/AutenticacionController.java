package com.example.proyectoAquachile.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    @GetMapping("/csrf")
    public Map<String, String> tokenCsrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token.getToken());
    }

    @GetMapping("/me")
    public Map<String, String> sesionActual(Authentication authentication) {
        return datosUsuario(authentication);
    }

    public static Map<String, String> datosUsuario(Authentication authentication) {
        String rol = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ANALISTA")) ? "ANALISTA" : "EVALUADOR";
        return Map.of("usuario", authentication.getName(), "rol", rol);
    }
}
