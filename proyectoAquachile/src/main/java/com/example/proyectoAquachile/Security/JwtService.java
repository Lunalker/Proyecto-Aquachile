package com.example.proyectoAquachile.Security;

import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JwtService {

    public static final String NOMBRE_COOKIE_ACCESO = "AQUACHILE_ACCESS";

    private final SecretKey secretKey;
    private final String issuer;
    private final long ttlMinutes;
    private final boolean cookieSecure;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final UsuarioRepository usuarioRepository;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:aquachile-api}") String issuer,
            @Value("${jwt.ttl-minutes:20}") long ttlMinutes,
            @Value("${jwt.cookie-secure:false}") boolean cookieSecure,
            UsuarioRepository usuarioRepository) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes para firmar HS256.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.issuer = issuer;
        this.ttlMinutes = ttlMinutes > 0 ? ttlMinutes : 20L;
        this.cookieSecure = cookieSecure;
        this.usuarioRepository = usuarioRepository;
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(this.secretKey));
        this.jwtDecoder = construirDecoder();
    }

    private JwtDecoder construirDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(this.secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(this.issuer);
        OAuth2TokenValidator<Jwt> versionValidator = jwt -> {
            String username = jwt.getSubject();
            Object claimVersion = jwt.getClaim("tokenVersion");
            if (username == null || username.isBlank() || claimVersion == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token sin sujeto o versión válida.", null));
            }
            long versionToken;
            if (claimVersion instanceof Number num) {
                versionToken = num.longValue();
            } else {
                try {
                    versionToken = Long.parseLong(claimVersion.toString());
                } catch (NumberFormatException e) {
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Formato inválido de tokenVersion.", null));
                }
            }

            Optional<Usuario> optUsuario = usuarioRepository.findByUsername(username);
            if (optUsuario.isEmpty()) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Usuario asociado al token no existe.", null));
            }
            Usuario usuario = optUsuario.get();
            if (!usuario.isActivo() || !Objects.equals(usuario.getTokenVersion(), versionToken)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "El token ha sido revocado o la cuenta está inactiva.", null));
            }
            return OAuth2TokenValidatorResult.success();
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, versionValidator));
        return decoder;
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        List<String> roles = (usuario.getRol() == null || usuario.getRol().isBlank())
                ? List.of()
                : List.of(usuario.getRol().trim().toUpperCase(Locale.ROOT));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(this.issuer)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(this.ttlMinutes, ChronoUnit.MINUTES))
                .subject(usuario.getUsername())
                .claim("roles", roles)
                .claim("tokenVersion", usuario.getTokenVersion())
                .build();

        return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public ResponseCookie crearCookieAcceso(String token) {
        return ResponseCookie.from(NOMBRE_COOKIE_ACCESO, token)
                .httpOnly(true)
                .secure(this.cookieSecure)
                .sameSite("Lax")
                .path("/api")
                .maxAge(Duration.ofMinutes(this.ttlMinutes))
                .build();
    }

    public ResponseCookie crearCookieExpirada() {
        return ResponseCookie.from(NOMBRE_COOKIE_ACCESO, "")
                .httpOnly(true)
                .secure(this.cookieSecure)
                .sameSite("Lax")
                .path("/api")
                .maxAge(Duration.ZERO)
                .build();
    }

    public JwtDecoder getJwtDecoder() {
        return this.jwtDecoder;
    }

    public BearerTokenResolver crearCookieBearerTokenResolver() {
        return (HttpServletRequest request) -> {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            for (Cookie cookie : cookies) {
                if (NOMBRE_COOKIE_ACCESO.equals(cookie.getName())) {
                    String valor = cookie.getValue();
                    return (valor != null && !valor.isBlank()) ? valor.trim() : null;
                }
            }
            return null;
        };
    }

    public JwtAuthenticationConverter crearJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object rawRoles = jwt.getClaim("roles");
            if (!(rawRoles instanceof Collection<?> listaRoles)) {
                return List.of();
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (Object item : listaRoles) {
                if (item != null) {
                    String r = item.toString().trim().toUpperCase(Locale.ROOT);
                    if (!r.isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }
}