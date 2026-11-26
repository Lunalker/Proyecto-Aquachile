package com.example.proyectoAquachile.Security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.getJwtDecoder();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver(JwtService jwtService) {
        BearerTokenResolver resolverBase = jwtService.crearCookieBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            if (path.startsWith("/api/ofertas/publicas")
                    || path.equals("/api/postulaciones/publicas")
                    || path.equals("/api/auth/login")
                    || path.equals("/api/auth/registro")
                    || path.equals("/api/auth/verificar-codigo")
                    || path.equals("/api/auth/reenviar-codigo")
                    || path.equals("/api/auth/csrf")) {
                return null;
            }
            return resolverBase.resolve(request);
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(JwtService jwtService) {
        return jwtService.crearJwtAuthenticationConverter();
    }

    @Bean
    public SecurityFilterChain seguridad(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            BearerTokenResolver bearerTokenResolver,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/registro",
                                "/api/auth/verificar-codigo",
                                "/api/auth/reenviar-codigo",
                                "/api/postulaciones/publicas"
                        ))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Autenticación pública y CSRF
                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/api/auth/registro",
                                "/api/auth/verificar-codigo",
                                "/api/auth/reenviar-codigo"
                        ).permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/cuentas-internas", "/api/auth/usuarios/*/rol").hasRole("ANALISTA")

                        // Ofertas públicas y carga pública de postulaciones
                        .requestMatchers(HttpMethod.GET, "/api/ofertas/publicas/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/postulaciones/publicas").permitAll()

                        // Reglas específicas de IA y revisión humana (por postulación y heredadas por candidato/solicitud)
                        .requestMatchers(HttpMethod.POST, "/api/postulaciones/*/analizar-ia").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.PUT, "/api/postulaciones/*/validar").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.POST, "/api/documentos/candidatos/*/analizar-ia").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.PUT, "/api/documentos/candidatos/*/validar").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/documentos/candidatos/*/analisis").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers(HttpMethod.GET, "/api/documentos/solicitudes/*/analisis-validado").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers(HttpMethod.POST, "/api/documentos/solicitudes/*/preguntas-apoyo").hasAnyRole("ANALISTA", "EVALUADOR")

                        // Matriz de requisitos del cargo
                        .requestMatchers(HttpMethod.GET, "/api/requisitos-cargo/**").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers(HttpMethod.POST, "/api/requisitos-cargo/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.DELETE, "/api/requisitos-cargo/**").hasRole("ANALISTA")

                        // Gestión documental interna
                        .requestMatchers(HttpMethod.POST, "/api/documentos/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/documentos/candidatos/**").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/documentos/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/postulaciones/**").hasAnyRole("ANALISTA", "EVALUADOR")

                        // Candidatos, usuarios, solicitudes y evaluaciones
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/evaluadores").hasRole("ANALISTA")
                        .requestMatchers("/api/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes/**").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.PUT, "/api/solicitudes/*/estado").hasRole("EVALUADOR")
                        .requestMatchers(HttpMethod.GET, "/api/evaluaciones/**").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers("/api/evaluaciones/**").hasRole("EVALUADOR")

                        .anyRequest().denyAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, err) -> res.sendError(401, "Debes iniciar sesión"))
                        .accessDeniedHandler((req, res, err) -> res.sendError(403, "No tienes permiso para esta operación")));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-XSRF-TOKEN", "Accept"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}