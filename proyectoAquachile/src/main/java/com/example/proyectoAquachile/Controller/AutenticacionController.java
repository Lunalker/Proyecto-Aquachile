package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import com.example.proyectoAquachile.Service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public AutenticacionController(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
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

        Optional<Usuario> existentePorCorreo = usuarioRepository.findByCorreo(correo);
        if (existentePorCorreo.isPresent() && existentePorCorreo.get().isActivo()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El correo electrónico ya está registrado y verificado."));
        }

        Optional<Usuario> existentePorUser = usuarioRepository.findByUsername(username);
        if (existentePorUser.isPresent() && !existentePorUser.get().getCorreo().equals(correo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El nombre de usuario ya está en uso."));
        }

        String codigo = generarCodigo6Digitos();
        Usuario usuario = existentePorCorreo.orElseGet(Usuario::new);
        usuario.setNombreCompleto(nombreCompleto);
        usuario.setCorreo(correo);
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(false);
        usuario.setCodigoVerificacion(codigo);
        usuario.setCodigoExpiracion(LocalDateTime.now().plusMinutes(10));

        usuarioRepository.save(usuario);

        try {
            emailService.enviarCodigoVerificacion(correo, nombreCompleto, codigo);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "No se pudo enviar el correo de verificación. Revisa la configuración de Gmail SMTP."
            ));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Código de verificación enviado a " + correo,
                "correo", correo,
                "username", username
        ));
    }

    @PostMapping("/verificar-codigo")
    public ResponseEntity<?> verificarCodigo(@RequestBody Map<String, String> body) {
        String correo = body.getOrDefault("correo", "").trim().toLowerCase();
        String codigo = body.getOrDefault("codigo", "").trim();

        if (correo.isEmpty() || codigo.length() != 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresa el código de 6 dígitos enviado a tu correo."));
        }

        Optional<Usuario> optUsuario = usuarioRepository.findByCorreo(correo);
        if (optUsuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No existe un registro pendiente para ese correo."));
        }

        Usuario usuario = optUsuario.get();
        if (usuario.isActivo()) {
            return ResponseEntity.ok(Map.of("mensaje", "La cuenta ya estaba verificada.", "username", usuario.getUsername()));
        }

        if (usuario.getCodigoVerificacion() == null || !usuario.getCodigoVerificacion().equals(codigo)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El código ingresado es incorrecto."));
        }

        if (usuario.getCodigoExpiracion() == null || usuario.getCodigoExpiracion().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "El código ha expirado. Solicita uno nuevo."));
        }

        usuario.setActivo(true);
        usuario.setCodigoVerificacion(null);
        usuario.setCodigoExpiracion(null);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Correo verificado correctamente. Ya puedes iniciar sesión.",
                "username", usuario.getUsername()
        ));
    }

    @PostMapping("/reenviar-codigo")
    public ResponseEntity<?> reenviarCodigo(@RequestBody Map<String, String> body) {
        String correo = body.getOrDefault("correo", "").trim().toLowerCase();
        Optional<Usuario> optUsuario = usuarioRepository.findByCorreo(correo);

        if (optUsuario.isEmpty() || optUsuario.get().isActivo()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No hay una verificación pendiente para este correo."));
        }

        Usuario usuario = optUsuario.get();
        String nuevoCodigo = generarCodigo6Digitos();
        usuario.setCodigoVerificacion(nuevoCodigo);
        usuario.setCodigoExpiracion(LocalDateTime.now().plusMinutes(10));
        usuarioRepository.save(usuario);

        try {
            emailService.enviarCodigoVerificacion(usuario.getCorreo(), usuario.getNombreCompleto(), nuevoCodigo);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "No se pudo reenviar el correo."));
        }

        return ResponseEntity.ok(Map.of("mensaje", "Se envió un nuevo código a " + usuario.getCorreo()));
    }

    private String generarCodigo6Digitos() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public static Map<String, String> datosUsuario(Authentication authentication) {
        String rol = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ANALISTA")) ? "ANALISTA" : "EVALUADOR";
        return Map.of("usuario", authentication.getName(), "rol", rol);
    }
}