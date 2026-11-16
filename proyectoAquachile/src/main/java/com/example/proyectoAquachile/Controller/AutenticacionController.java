package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AutenticacionController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/csrf")
    public Map<String, String> tokenCsrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token.getToken());
    }

    @GetMapping("/me")
    public Map<String, String> sesionActual(Authentication authentication) {
        return datosUsuario(authentication);
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registrarUsuario(@RequestBody Map<String, String> body) {
        String nombreCompleto = body.getOrDefault("nombreCompleto", "").trim();
        String correo = body.getOrDefault("correo", "").trim().toLowerCase();
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        String rol = body.getOrDefault("rol", "").trim().toUpperCase();

        if (nombreCompleto.isEmpty() || correo.isEmpty() || username.isEmpty() || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa todos los campos (contraseña mínimo 6 caracteres)."));
        }
        if (!List.of("ANALISTA", "EVALUADOR").contains(rol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rol debe ser ANALISTA o EVALUADOR."));
        }
        if (usuarioRepository.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El nombre de usuario ya está en uso."));
        }
        if (usuarioRepository.existsByCorreo(correo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El correo electrónico ya está registrado."));
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombreCompleto(nombreCompleto);
        nuevo.setCorreo(correo);
        nuevo.setUsername(username);
        nuevo.setPassword(passwordEncoder.encode(password));
        nuevo.setRol(rol);
        usuarioRepository.save(nuevo);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Usuario registrado correctamente",
                "username", nuevo.getUsername(),
                "rol", nuevo.getRol()
        ));
    }

    public static Map<String, String> datosUsuario(Authentication authentication) {
        String rol = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ANALISTA")) ? "ANALISTA" : "EVALUADOR";
        return Map.of("usuario", authentication.getName(), "rol", rol);
    }
}