package com.example.proyectoAquachile.Security;

import com.example.proyectoAquachile.Controller.AutenticacionController;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public SecurityFilterChain seguridad(HttpSecurity http, ObjectMapper json) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .authorizeHttpRequests(auth -> auth

                        // Postulación externa pública (crear candidato y subir su CV)
                        .requestMatchers(HttpMethod.POST, "/api/candidatos").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/documentos/candidatos/**").permitAll()
                        // Gestión interna: consulta, edición y eliminación solo para personal autorizado
                        .requestMatchers(HttpMethod.GET, "/api/documentos/candidatos/**").hasAnyRole("ANALISTA", "EVALUADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/documentos/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.PUT, "/api/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(HttpMethod.DELETE, "/api/candidatos/**").hasRole("ANALISTA")
                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/login",
                                "/api/auth/registro",
                                "/api/auth/verificar-codigo",
                                "/api/auth/reenviar-codigo"
                        ).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/registro").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/usuarios/evaluadores").hasRole("ANALISTA")
    // ... mantén el resto de tus reglas actuales sin cambios ...
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                // CV: lectura para Analista/Evaluador; modificaciones solo Analista.
                .requestMatchers(HttpMethod.GET, "/api/documentos/candidatos/**").hasAnyRole("ANALISTA", "EVALUADOR")
                .requestMatchers(HttpMethod.POST, "/api/documentos/candidatos/**").hasRole("ANALISTA")
                .requestMatchers(HttpMethod.DELETE, "/api/documentos/candidatos/**").hasRole("ANALISTA")
                .requestMatchers(HttpMethod.GET, "/api/candidatos/**").hasRole("ANALISTA")
                .requestMatchers("/api/candidatos/**").hasRole("ANALISTA")
                .requestMatchers(HttpMethod.GET, "/api/solicitudes/**").hasAnyRole("ANALISTA", "EVALUADOR")
                .requestMatchers(HttpMethod.POST, "/api/solicitudes/**").hasRole("ANALISTA")
                .requestMatchers(HttpMethod.PUT, "/api/solicitudes/*/estado").hasRole("EVALUADOR")
                .requestMatchers(HttpMethod.GET, "/api/evaluaciones/**").hasAnyRole("ANALISTA", "EVALUADOR")
                .requestMatchers("/api/evaluaciones/**").hasRole("EVALUADOR")
                .anyRequest().denyAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("usuario")
                .passwordParameter("clave")
                .successHandler((req, res, authentication) -> {
                    res.setStatus(200);
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getWriter(), AutenticacionController.datosUsuario(authentication));
                })
                .failureHandler((req, res, exception) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getWriter(), Map.of("error", "Usuario o contraseña incorrectos"));
                })
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, authentication) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getWriter(), Map.of("mensaje", "Sesión cerrada"));
                }))
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
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Accept"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
