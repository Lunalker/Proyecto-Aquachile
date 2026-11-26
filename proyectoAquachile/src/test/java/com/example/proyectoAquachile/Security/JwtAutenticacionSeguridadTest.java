package com.example.proyectoAquachile.Security;

import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAutenticacionSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.issuer:aquachile-api}")
    private String jwtIssuer;

    @BeforeEach
    void prepararUsuarios() {
        crearOActualizar("analista.jwt", "analista.jwt@aquachile.cl", "ClaveSegura123", "ANALISTA");
        crearOActualizar("evaluador.jwt", "evaluador.jwt@aquachile.cl", "ClaveSegura123", "EVALUADOR");
    }

    private void crearOActualizar(String username, String correo, String rawPass, String rol) {
        Usuario u = usuarioRepository.findByUsername(username).orElseGet(Usuario::new);
        u.setNombreCompleto("Usuario Test " + rol);
        u.setUsername(username);
        u.setCorreo(correo);
        u.setPassword(passwordEncoder.encode(rawPass));
        u.setRol(rol);
        u.setActivo(true);
        u.setTokenVersion(0L);
        usuarioRepository.save(u);
    }

    private record CsrfContext(String token, Cookie xsrfCookie) {}

    private CsrfContext obtenerCsrf() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        Cookie xsrfCookie = res.getResponse().getCookie("XSRF-TOKEN");
        return new CsrfContext(json.get("token").asText(), xsrfCookie);
    }

    private Cookie autenticarYObtenerCookie(String usuario, String clave) throws Exception {
        CsrfContext csrf = obtenerCsrf();
        var req = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-CSRF-TOKEN", csrf.token())
                .content("{\"usuario\":\"" + usuario + "\",\"clave\":\"" + clave + "\"}");
        if (csrf.xsrfCookie() != null) {
            req.cookie(csrf.xsrfCookie());
        }
        MvcResult res = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = res.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("AQUACHILE_ACCESS=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/api");

        Cookie accessCookie = res.getResponse().getCookie("AQUACHILE_ACCESS");
        assertThat(accessCookie).isNotNull();
        return accessCookie;
    }

    @Test
    void loginExitosoEmiteCookieHttpOnlyYPermiteConsultarMe() throws Exception {
        Cookie accessCookie = autenticarYObtenerCookie("analista.jwt", "ClaveSegura123");

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("analista.jwt"))
                .andExpect(jsonPath("$.rol").value("ANALISTA"));
    }

    @Test
    void credencialesIncorrectasRetornan401SinCookieAcceso() throws Exception {
        CsrfContext csrf = obtenerCsrf();
        var req = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-CSRF-TOKEN", csrf.token())
                .content("{\"usuario\":\"analista.jwt\",\"clave\":\"ClaveErronea\"}");
        if (csrf.xsrfCookie() != null) {
            req.cookie(csrf.xsrfCookie());
        }

        MvcResult res = mockMvc.perform(req)
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(res.getResponse().getCookie("AQUACHILE_ACCESS")).isNull();
    }

    @Test
    void rechazaPostSinTokenCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"analista.jwt\",\"clave\":\"ClaveSegura123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protegeRutasPorRolEntreAnalistaYEvaluador() throws Exception {
        Cookie cookieEvaluador = autenticarYObtenerCookie("evaluador.jwt", "ClaveSegura123");

        // Evaluador NO puede acceder a endpoints exclusivos de ANALISTA
        mockMvc.perform(get("/api/candidatos").cookie(cookieEvaluador))
                .andExpect(status().isForbidden());

        // Evaluador SÍ puede consultar solicitudes compartidas
        mockMvc.perform(get("/api/solicitudes").cookie(cookieEvaluador))
                .andExpect(status().isOk());
    }

    @Test
    void registroPublicoRechazaAutoasignacionDeRolPrivilegiado() throws Exception {
        CsrfContext csrf = obtenerCsrf();
        var req = post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-CSRF-TOKEN", csrf.token())
                .content("""
                        {
                          "nombreCompleto": "Intruso Test",
                          "correo": "intruso.test@aquachile.cl",
                          "username": "intruso.test",
                          "password": "Password123",
                          "rol": "ANALISTA"
                        }
                        """);
        if (csrf.xsrfCookie() != null) {
            req.cookie(csrf.xsrfCookie());
        }

        mockMvc.perform(req).andExpect(status().isForbidden());
    }

    @Test
    void logoutRevocaTokenMedianteTokenVersionYEliminaCookie() throws Exception {
        Cookie accessCookie = autenticarYObtenerCookie("analista.jwt", "ClaveSegura123");
        CsrfContext csrf = obtenerCsrf();

        var logoutReq = post("/api/auth/logout")
                .cookie(accessCookie)
                .header("X-CSRF-TOKEN", csrf.token());
        if (csrf.xsrfCookie() != null) {
            logoutReq.cookie(csrf.xsrfCookie());
        }

        MvcResult logoutRes = mockMvc.perform(logoutReq)
                .andExpect(status().isOk())
                .andReturn();

        String setCookieLogout = logoutRes.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieLogout).contains("AQUACHILE_ACCESS=");
        assertThat(setCookieLogout).contains("Max-Age=0");

        // Reutilizar el token antiguo debe fallar con 401 porque tokenVersion cambió
        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaTokenExpiradoOConFirmaInvalida() throws Exception {
        // Token manipulado / inválido
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("AQUACHILE_ACCESS", "token.invalido.firma")))
                .andExpect(status().isUnauthorized());

        // Token expirado firmado con la clave actual
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant pasado = Instant.now().minus(30, ChronoUnit.MINUTES);
        JwtClaimsSet claimsExpirados = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(pasado.minus(20, ChronoUnit.MINUTES))
                .expiresAt(pasado)
                .subject("analista.jwt")
                .claim("roles", List.of("ANALISTA"))
                .claim("tokenVersion", 0L)
                .build();
        String jwtExpirado = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claimsExpirados)).getTokenValue();

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("AQUACHILE_ACCESS", jwtExpirado)))
                .andExpect(status().isUnauthorized());
    }
}