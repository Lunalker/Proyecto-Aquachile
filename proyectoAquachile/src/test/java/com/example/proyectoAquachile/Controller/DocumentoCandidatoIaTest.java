package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvResponseDTO;
import com.example.proyectoAquachile.Dto.ValidacionAnalisisRequestDTO;
import com.example.proyectoAquachile.Model.Candidato;
import com.example.proyectoAquachile.Model.DocumentoCandidato;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import com.example.proyectoAquachile.Repository.DocumentoCandidatoRepository;
import com.example.proyectoAquachile.Service.GestionAnalisisCvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentoCandidatoIaTest {

    @Mock
    private DocumentoCandidatoRepository documentos;

    @Mock
    private CandidatoRepository candidatos;

    @Mock
    private GestionAnalisisCvService gestionAnalisisService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        DocumentoCandidatoController controller = new DocumentoCandidatoController(
                documentos, candidatos, gestionAnalisisService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void reemplazarCvInvalidaAnalisisPrevios() throws Exception {
        Candidato candidato = new Candidato();
        candidato.setId(1L);
        candidato.setNombre("Sofia Valenzuela");

        byte[] pdfBytes = "%PDF-1.4 contenido simulado".getBytes();
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cv_nuevo.pdf", "application/pdf", pdfBytes);

        when(candidatos.findById(1L)).thenReturn(Optional.of(candidato));
        when(documentos.findByCandidato_Id(1L)).thenReturn(Optional.empty());
        when(documentos.save(any(DocumentoCandidato.class))).thenAnswer(inv -> {
            DocumentoCandidato d = inv.getArgument(0);
            d.setCandidato(candidato);
            return d;
        });

        mockMvc.perform(multipart("/api/documentos/candidatos/1").file(archivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analizableIa").value(true));

        verify(gestionAnalisisService, times(1)).invalidarAnalisisPrevios(1L);
    }

    @Test
    void evaluadorSoloConsultaAnalisisValidados() throws Exception {
        var authEvaluador = new UsernamePasswordAuthenticationToken(
                "evaluador1", "pass", List.of(new SimpleGrantedAuthority("ROLE_EVALUADOR")));

        when(gestionAnalisisService.obtenerAnalisisActivo(1L, true)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documentos/candidatos/1/analisis").principal(authEvaluador))
                .andExpect(status().isNoContent());

        verify(gestionAnalisisService).obtenerAnalisisActivo(1L, true);
    }

    @Test
    void analistaValidaAntecedentesCorrectamente() throws Exception {
        var authAnalista = new UsernamePasswordAuthenticationToken(
                "analista1", "pass", List.of(new SimpleGrantedAuthority("ROLE_ANALISTA")));

        AnalisisCvDTO dto = new AnalisisCvDTO(
                "Sofia Valenzuela", "Ingeniera Acuicola", "Resumen validado",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        ValidacionAnalisisRequestDTO req = new ValidacionAnalisisRequestDTO(
                dto, "Verificado por analista", true);

        AnalisisCvResponseDTO resp = new AnalisisCvResponseDTO(
                10L, 1L, "Sofia Valenzuela", "Jefe Centro", "Produccion",
                "cv.pdf", "hash123", "llama-3.1-8b-instant", LocalDateTime.now(),
                "GENERADO", "VALIDADO", "analista1", LocalDateTime.now(),
                "Verificado por analista", dto, dto, null
        );

        when(gestionAnalisisService.guardarRevisionOValidar(eq(1L), any(), eq("analista1")))
                .thenReturn(resp);

        mockMvc.perform(put("/api/documentos/candidatos/1/validar")
                        .principal(authAnalista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoRevision").value("VALIDADO"))
                .andExpect(jsonPath("$.usuarioValidador").value("analista1"));
    }
}