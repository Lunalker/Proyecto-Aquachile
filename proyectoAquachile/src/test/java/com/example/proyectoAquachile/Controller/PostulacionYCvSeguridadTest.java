package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.OfertaLaboral;
import com.example.proyectoAquachile.Model.Usuario;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import com.example.proyectoAquachile.Repository.OfertaLaboralRepository;
import com.example.proyectoAquachile.Repository.PostulacionRepository;
import com.example.proyectoAquachile.Repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PostulacionYCvSeguridadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfertaLaboralRepository ofertaRepository;

    @Autowired
    private PostulacionRepository postulacionRepository;

    @Autowired
    private CandidatoRepository candidatoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private OfertaLaboral ofertaPublicada;
    private OfertaLaboral ofertaBorrador;

    @BeforeEach
    void prepararDatos() {
        postulacionRepository.deleteAll();
        candidatoRepository.deleteAll();
        ofertaRepository.deleteAll();

        OfertaLaboral pub = new OfertaLaboral();
        pub.setCargo("Ingeniero de Procesos Acuícolas");
        pub.setCategoria("Procesos y Planta");
        pub.setUbicacion("Calbuco");
        pub.setModalidad("Presencial");
        pub.setFechaPublicacion(LocalDateTime.now());
        pub.setCupos(2);
        pub.setDescripcion("Gestión de procesos productivos");
        pub.setFunciones("Supervisar indicadores de rendimiento");
        pub.setRequisitos("Título profesional afín");
        pub.setEstadoPublicacion(OfertaLaboral.ESTADO_PUBLICADA);
        ofertaPublicada = ofertaRepository.save(pub);

        OfertaLaboral borrador = new OfertaLaboral();
        borrador.setCargo("Cargo No Publicado");
        borrador.setCategoria("Administración");
        borrador.setUbicacion("Puerto Montt");
        borrador.setModalidad("Presencial");
        borrador.setFechaPublicacion(LocalDateTime.now());
        borrador.setCupos(1);
        borrador.setDescripcion("Oferta interna en borrador");
        borrador.setFunciones("Funciones internas");
        borrador.setRequisitos("Requisitos internos");
        borrador.setEstadoPublicacion(OfertaLaboral.ESTADO_BORRADOR);
        ofertaBorrador = ofertaRepository.save(borrador);

        Usuario analista = usuarioRepository.findByUsername("analista.cv").orElseGet(Usuario::new);
        analista.setNombreCompleto("Analista CV Test");
        analista.setUsername("analista.cv");
        analista.setCorreo("analista.cv@aquachile.cl");
        analista.setPassword(passwordEncoder.encode("ClaveSegura123"));
        analista.setRol("ANALISTA");
        analista.setActivo(true);
        analista.setTokenVersion(0L);
        usuarioRepository.save(analista);
    }

    private record CsrfContext(String token, Cookie xsrfCookie) {}

    private CsrfContext obtenerCsrf() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return new CsrfContext(json.get("token").asText(), res.getResponse().getCookie("XSRF-TOKEN"));
    }

    private Cookie autenticarAnalista() throws Exception {
        CsrfContext csrf = obtenerCsrf();
        var req = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-CSRF-TOKEN", csrf.token())
                .content("{\"usuario\":\"analista.cv\",\"clave\":\"ClaveSegura123\"}");
        if (csrf.xsrfCookie() != null) req.cookie(csrf.xsrfCookie());
        MvcResult res = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
        return res.getResponse().getCookie("AQUACHILE_ACCESS");
    }

    private byte[] generarPdfValido() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generarDocValido() {
        return new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1, 0x00, 0x01};
    }

    private byte[] generarDocxValido() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<w:document><w:body><w:p>CV Prueba</w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    void postulacionPublicaConPdfValidoCreaContenedorUuidYPermiteDescargaAutorizada() throws Exception {
        byte[] pdfBytes = generarPdfValido();
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cv_sofia_valenzuela.pdf", "application/pdf", pdfBytes);

        CsrfContext csrf = obtenerCsrf();
        var req = multipart("/api/postulaciones/publicas")
                .file(archivo)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Sofía Valenzuela")
                .param("correo", "sofia.valenzuela@ejemplo.cl")
                .param("telefono", "+56912345678")
                .header("X-CSRF-TOKEN", csrf.token());
        if (csrf.xsrfCookie() != null) req.cookie(csrf.xsrfCookie());

        MvcResult creadaRes = mockMvc.perform(req)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contenedorUuid").isNotEmpty())
                .andExpect(jsonPath("$.nombreContenedor").isNotEmpty())
                .andExpect(jsonPath("$.tipoMimeCv").value("application/pdf"))
                .andReturn();

        JsonNode body = objectMapper.readTree(creadaRes.getResponse().getContentAsString());
        long postulacionId = body.get("id").asLong();

        // El acceso público sin autenticar a los documentos privados debe ser rechazado (401)
        mockMvc.perform(get("/api/postulaciones/" + postulacionId + "/cv"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/postulaciones/" + postulacionId + "/cv/descargar"))
                .andExpect(status().isUnauthorized());

        // Con sesión JWT de ANALISTA debe permitir consulta y descarga segura
        Cookie cookieAnalista = autenticarAnalista();
        mockMvc.perform(get("/api/postulaciones/" + postulacionId + "/cv").cookie(cookieAnalista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postulacionId").value(postulacionId))
                .andExpect(jsonPath("$.nombreOriginal").value("cv_sofia_valenzuela.pdf"));

        MvcResult descargaRes = mockMvc.perform(get("/api/postulaciones/" + postulacionId + "/cv/descargar").cookie(cookieAnalista))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn();

        assertThat(descargaRes.getResponse().getContentAsByteArray()).isEqualTo(pdfBytes);
    }

    @Test
    void aceptaArchivosDocYDocxAutenticos() throws Exception {
        MockMultipartFile docFile = new MockMultipartFile(
                "archivo", "cv_antiguo.doc", "application/msword", generarDocValido());
        CsrfContext csrf1 = obtenerCsrf();
        var reqDoc = multipart("/api/postulaciones/publicas")
                .file(docFile)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Postulante Doc")
                .param("correo", "doc@ejemplo.cl")
                .param("telefono", "+56911111111")
                .header("X-CSRF-TOKEN", csrf1.token());
        if (csrf1.xsrfCookie() != null) reqDoc.cookie(csrf1.xsrfCookie());

        mockMvc.perform(reqDoc)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoMimeCv").value("application/msword"));

        MockMultipartFile docxFile = new MockMultipartFile(
                "archivo", "cv_moderno.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                generarDocxValido());
        CsrfContext csrf2 = obtenerCsrf();
        var reqDocx = multipart("/api/postulaciones/publicas")
                .file(docxFile)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Postulante Docx")
                .param("correo", "docx@ejemplo.cl")
                .param("telefono", "+56922222222")
                .header("X-CSRF-TOKEN", csrf2.token());
        if (csrf2.xsrfCookie() != null) reqDocx.cookie(csrf2.xsrfCookie());

        mockMvc.perform(reqDocx)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoMimeCv").value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void rechazaArchivoFalsificadoYHaceRollbackTransaccional() throws Exception {
        MockMultipartFile falsoPdf = new MockMultipartFile(
                "archivo", "ejecutable_falso.pdf", "application/pdf",
                "MZ_ESTO_NO_ES_UN_PDF".getBytes(StandardCharsets.UTF_8));

        CsrfContext csrf = obtenerCsrf();
        var req = multipart("/api/postulaciones/publicas")
                .file(falsoPdf)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Usuario Archivo Falso")
                .param("correo", "falso@ejemplo.cl")
                .param("telefono", "+56933333333")
                .header("X-CSRF-TOKEN", csrf.token());
        if (csrf.xsrfCookie() != null) req.cookie(csrf.xsrfCookie());

        mockMvc.perform(req).andExpect(status().isBadRequest());

        // Rollback completo: no debe quedar candidato ni postulación huérfana
        assertThat(candidatoRepository.findByCorreoIgnoreCase("falso@ejemplo.cl")).isEmpty();
        assertThat(postulacionRepository.count()).isZero();
    }

    @Test
    void rechazaArchivoMayorA5MbYPostulacionDuplicada() throws Exception {
        byte[] excesoBytes = new byte[(5 * 1024 * 1024) + 128];
        excesoBytes[0] = '%';
        excesoBytes[1] = 'P';
        excesoBytes[2] = 'D';
        excesoBytes[3] = 'F';
        excesoBytes[4] = '-';

        MockMultipartFile archivoGrande = new MockMultipartFile(
                "archivo", "cv_pesado.pdf", "application/pdf", excesoBytes);

        CsrfContext csrf1 = obtenerCsrf();
        var reqGrande = multipart("/api/postulaciones/publicas")
                .file(archivoGrande)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Postulante Pesado")
                .param("correo", "pesado@ejemplo.cl")
                .param("telefono", "+56944444444")
                .header("X-CSRF-TOKEN", csrf1.token());
        if (csrf1.xsrfCookie() != null) reqGrande.cookie(csrf1.xsrfCookie());

        mockMvc.perform(reqGrande).andExpect(status().is4xxClientError());
        assertThat(postulacionRepository.count()).isZero();

        // Primera postulación válida -> 201 Created
        MockMultipartFile pdfOk = new MockMultipartFile(
                "archivo", "cv_ok.pdf", "application/pdf", generarPdfValido());
        CsrfContext csrf2 = obtenerCsrf();
        var reqOk = multipart("/api/postulaciones/publicas")
                .file(pdfOk)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Postulante Duplicado")
                .param("correo", "duplicado@ejemplo.cl")
                .param("telefono", "+56955555555")
                .header("X-CSRF-TOKEN", csrf2.token());
        if (csrf2.xsrfCookie() != null) reqOk.cookie(csrf2.xsrfCookie());

        mockMvc.perform(reqOk).andExpect(status().isCreated());

        // Segunda postulación del mismo candidato a la misma oferta -> 409 Conflict
        CsrfContext csrf3 = obtenerCsrf();
        var reqDup = multipart("/api/postulaciones/publicas")
                .file(pdfOk)
                .param("ofertaId", String.valueOf(ofertaPublicada.getId()))
                .param("nombre", "Postulante Duplicado")
                .param("correo", "duplicado@ejemplo.cl")
                .param("telefono", "+56955555555")
                .header("X-CSRF-TOKEN", csrf3.token());
        if (csrf3.xsrfCookie() != null) reqDup.cookie(csrf3.xsrfCookie());

        mockMvc.perform(reqDup).andExpect(status().isConflict());
        assertThat(postulacionRepository.count()).isEqualTo(1);
    }

    @Test
    void rechazaPostulacionAOfertaNoPublicadaYBloqueaAntiguoEndpointPublico() throws Exception {
        MockMultipartFile pdfOk = new MockMultipartFile(
                "archivo", "cv_ok.pdf", "application/pdf", generarPdfValido());
        CsrfContext csrf = obtenerCsrf();
        var reqBorrador = multipart("/api/postulaciones/publicas")
                .file(pdfOk)
                .param("ofertaId", String.valueOf(ofertaBorrador.getId()))
                .param("nombre", "Postulante Borrador")
                .param("correo", "borrador@ejemplo.cl")
                .param("telefono", "+56966666666")
                .header("X-CSRF-TOKEN", csrf.token());
        if (csrf.xsrfCookie() != null) reqBorrador.cookie(csrf.xsrfCookie());

        mockMvc.perform(reqBorrador).andExpect(status().isNotFound());
        assertThat(candidatoRepository.findByCorreoIgnoreCase("borrador@ejemplo.cl")).isEmpty();

        // Antiguo endpoint POST /api/documentos/candidatos/{id} ya no es público
        CsrfContext csrf2 = obtenerCsrf();
        var reqLegacy = multipart("/api/documentos/candidatos/1")
                .file(pdfOk)
                .header("X-CSRF-TOKEN", csrf2.token());
        if (csrf2.xsrfCookie() != null) reqLegacy.cookie(csrf2.xsrfCookie());

        mockMvc.perform(reqLegacy).andExpect(status().isUnauthorized());
    }
}