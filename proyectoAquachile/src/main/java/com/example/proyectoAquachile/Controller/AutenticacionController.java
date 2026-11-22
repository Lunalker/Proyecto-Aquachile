package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import com.example.proyectoAquachile.Security.JwtService;
import com.example.proyectoAquachile.Service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    private static final List<String> ROLES_INTERNOS = List.of("ANALISTA", "EVALUADOR");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final String inviteCode;
    private final SecureRandom random = new SecureRandom();

    public AutenticacionController(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            @Value("${aquachile.auth.invite-code:}") String inviteCode) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.inviteCode = inviteCode == null ? "" : inviteCode.trim();
    }

    @GetMapping("/csrf")
    public Map<String, String> tokenCsrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token != null ? token.getToken() : "");
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "usuario", required = false) String usuarioParam,
            @RequestParam(value = "clave", required = false) String claveParam,
            HttpServletResponse response) {

        String identificador = body != null && body.containsKey("usuario")
                ? body.getOrDefault("usuario", "").trim()
                : (usuarioParam == null ? "" : usuarioParam.trim());
        String clave = body != null && body.containsKey("clave")
                ? body.getOrDefault("clave", "")
                : (claveParam == null ? "" : claveParam);

        if (identificador.isEmpty() || clave.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario o contraseña incorrectos"));
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identificador, clave)
            );

            Usuario usuario = usuarioRepository.findByUsername(auth.getName())
                    .or(() -> usuarioRepository.findByCorreo(identificador.toLowerCase(Locale.ROOT)))
                    .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

            if (!usuario.isActivo()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "La cuenta aún no ha sido verificada por correo."));
            }

            String token = jwtService.generarToken(usuario);
            response.addHeader(HttpHeaders.SET_COOKIE, jwtService.crearCookieAcceso(token).toString());

            return ResponseEntity.ok(Map.of(
                    "usuario", usuario.getUsername(),
                    "rol", usuario.getRol()
            ));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "La cuenta no está activa. Verifica tu correo."));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario o contraseña incorrectos"));
        }
    }

    @GetMapping("/me")
    public Map<String, String> sesionActual(Authentication authentication) {
        return datosUsuario(authentication);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> cerrarSesion(
            Authentication authentication,
            HttpServletResponse response) {

        if (authentication != null && authentication.isAuthenticated()) {
            usuarioRepository.findByUsername(authentication.getName()).ifPresent(usuario -> {
                usuario.incrementarTokenVersion();
                usuarioRepository.save(usuario);
            });
        }

        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.crearCookieExpirada().toString());
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada"));
    }

    /**
     * Registro público con verificación de correo.
     * Impide la autoasignación libre de roles internos privilegiados (ANALISTA / EVALUADOR).
     */
    @PostMapping("/registro")
    public ResponseEntity<?> registrarUsuario(@RequestBody Map<String, String> body) {
        String nombreCompleto = body.getOrDefault("nombreCompleto", "").trim();
        String correo = body.getOrDefault("correo", "").trim().toLowerCase(Locale.ROOT);
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        String rolSolicitado = body.getOrDefault("rol", "").trim().toUpperCase(Locale.ROOT);
        String codigoInvitacionRecibido = body.getOrDefault("codigoInvitacion", "").trim();

        if (nombreCompleto.isEmpty() || correo.isEmpty() || username.isEmpty() || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa todos los campos (contraseña mínimo 6 caracteres)."));
        }

        String rolAsignado = "PENDIENTE";
        if (!rolSolicitado.isEmpty()) {
            if (!ROLES_INTERNOS.contains(rolSolicitado)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rol no reconocido."));
            }
            if (this.inviteCode.isEmpty() || !this.inviteCode.equals(codigoInvitacionRecibido)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "No está permitido seleccionar roles internos privilegiados desde el registro público."
                ));
            }
            rolAsignado = rolSolicitado;
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
        usuario.setRol(rolAsignado);
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
                "username", username,
                "rol", rolAsignado
        ));
    }

    /**
     * Mecanismo controlado exclusivo para ANALISTA: crea cuentas internas con rol asignado explícitamente.
     */
    @PostMapping("/cuentas-internas")
    public ResponseEntity<?> crearCuentaInterna(@RequestBody Map<String, String> body) {
        String nombreCompleto = body.getOrDefault("nombreCompleto", "").trim();
        String correo = body.getOrDefault("correo", "").trim().toLowerCase(Locale.ROOT);
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        String rol = body.getOrDefault("rol", "").trim().toUpperCase(Locale.ROOT);

        if (nombreCompleto.isEmpty() || correo.isEmpty() || username.isEmpty() || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Completa todos los campos (contraseña mínimo 6 caracteres)."));
        }
        if (!ROLES_INTERNOS.contains(rol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rol interno debe ser ANALISTA o EVALUADOR."));
        }
        if (usuarioRepository.findByCorreo(correo).isPresent() || usuarioRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El correo o usuario ya existe."));
        }

        Usuario usuario = new Usuario();
        usuario.setNombreCompleto(nombreCompleto);
        usuario.setCorreo(correo);
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", usuario.getId(),
                "username", usuario.getUsername(),
                "correo", usuario.getCorreo(),
                "rol", usuario.getRol()
        ));
    }

    /**
     * Mecanismo controlado exclusivo para ANALISTA: asigna o cambia el rol de una cuenta existente
     * e invalida sus tokens anteriores.
     */
    @PutMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> asignarRolUsuario(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String nuevoRol = body.getOrDefault("rol", "").trim().toUpperCase(Locale.ROOT);
        if (!ROLES_INTERNOS.contains(nuevoRol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rol debe ser ANALISTA o EVALUADOR."));
        }

        Optional<Usuario> optUsuario = usuarioRepository.findById(id);
        if (optUsuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }

        Usuario usuario = optUsuario.get();
        usuario.setRol(nuevoRol);
        usuario.incrementarTokenVersion();
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of(
                "id", usuario.getId(),
                "username", usuario.getUsername(),
                "rol", usuario.getRol()
        ));
    }

    @PostMapping("/verificar-codigo")
    public ResponseEntity<?> verificarCodigo(@RequestBody Map<String, String> body) {
        String correo = body.getOrDefault("correo", "").trim().toLowerCase(Locale.ROOT);
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
        String correo = body.getOrDefault("correo", "").trim().toLowerCase(Locale.ROOT);
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
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("PENDIENTE");
        return Map.of("usuario", authentication.getName(), "rol", rol);
    }
}