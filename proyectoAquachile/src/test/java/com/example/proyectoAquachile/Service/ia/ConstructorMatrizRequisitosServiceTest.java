package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.ExperienciaItemDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.RequisitoMatrizItemDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import com.example.proyectoAquachile.Service.ia.RespuestaIaCrudaDTO.RequisitoIaItemDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class ConstructorMatrizRequisitosServiceTest {

    private ConstructorMatrizRequisitosService constructorMatriz;

    @BeforeEach
    void setUp() {
        VerificadorEvidenciaService verificador = new VerificadorEvidenciaService();
        CalculadoraExperienciaService calculadora = new CalculadoraExperienciaService(
                Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), CalculadoraExperienciaService.ZONA_CHILE)
        );
        constructorMatriz = new ConstructorMatrizRequisitosService(verificador, calculadora);
    }

    @Test
    void degradaAEncontradoSinCitaVerificadaOExperienciaInsuficienteYMarcaOmisiones() {
        RequisitoCargo req1 = mockReq(10L, "Manejo de SCADA", true, null);
        RequisitoCargo req2 = mockReq(20L, "Experiencia en cultivo marino", true, 24);
        RequisitoCargo req3 = mockReq(30L, "Licencia de conducir", false, null);

        Map<Integer, String> paginas = Map.of(
                1, "Operador de centros de cultivo marino en Quellón desde Ene 2025 hasta Dic 2025."
        );

        List<ExperienciaItemDTO> experiencias = List.of(
                new ExperienciaItemDTO(
                        "Operador de centros de cultivo marino",
                        "Empresa Mar",
                        "Ene 2025 - Dic 2025",
                        "1 año",
                        List.of(),
                        new EvidenciaDTO(1, "Operador de centros de cultivo marino", "EXPERIENCIA", "VERIFICADO_EN_DOCUMENTO"),
                        "PENDIENTE"
                )
        );

        List<RequisitoIaItemDTO> respuestasIa = List.of(
                // req1: cita inventada -> debe degradarse
                new RequisitoIaItemDTO(
                        10L, "Manejo de SCADA", true, "ENCONTRADO",
                        List.of("SCADA"), List.of(), "Cumple",
                        new EvidenciaDTO(1, "Experto en SCADA industrial", "REQUISITO_CARGO", "PENDIENTE")
                ),
                // req2: cita literal válida, pero solo tiene 12 meses frente a 24 exigidos -> debe degradarse
                new RequisitoIaItemDTO(
                        20L, "Experiencia en cultivo marino", true, "ENCONTRADO",
                        List.of("cultivo marino"), List.of(0), "Tiene experiencia",
                        new EvidenciaDTO(1, "Operador de centros de cultivo marino", "REQUISITO_CARGO", "PENDIENTE")
                ),
                // ID 999 inexistente en BD -> debe ignorarse
                new RequisitoIaItemDTO(
                        999L, "Requisito inventado", true, "ENCONTRADO",
                        List.of(), List.of(), "Inventado",
                        new EvidenciaDTO(1, "Operador", "REQUISITO_CARGO", "PENDIENTE")
                )
                // req3 (30L) omitido por el LLM -> debe quedar en REQUIERE_VERIFICACION
        );

        List<RequisitoMatrizItemDTO> matriz = constructorMatriz.construirMatriz(
                List.of(req1, req2, req3),
                respuestasIa,
                experiencias,
                paginas,
                false
        );

        assertEquals(3, matriz.size());

        RequisitoMatrizItemDTO m1 = matriz.get(0);
        assertEquals(10L, m1.requisitoId());
        assertEquals("REQUIERE_VERIFICACION", m1.resultadoAnalisis());
        assertTrue(m1.observacionEvidencia().contains("degradado a verificación humana"));

        RequisitoMatrizItemDTO m2 = matriz.get(1);
        assertEquals(20L, m2.requisitoId());
        assertEquals("REQUIERE_VERIFICACION", m2.resultadoAnalisis());
        assertTrue(m2.observacionEvidencia().contains("Cálculo backend"));

        RequisitoMatrizItemDTO m3 = matriz.get(2);
        assertEquals(30L, m3.requisitoId());
        assertEquals("REQUIERE_VERIFICACION", m3.resultadoAnalisis());
        assertTrue(m3.observacionEvidencia().contains("omitió evaluar"));
    }

    @Test
    void noPermiteDeclararAusenciaCuandoDocumentoQuedoTruncado() {
        RequisitoCargo req = mockReq(1L, "Certificación ISO 9001", true, null);

        List<RequisitoIaItemDTO> respuestasIa = List.of(
                new RequisitoIaItemDTO(
                        1L, "Certificación ISO 9001", true, "NO_IDENTIFICADO_EN_CV",
                        List.of(), List.of(), "No aparece",
                        new EvidenciaDTO(null, "", "REQUISITO_CARGO", "NO_IDENTIFICADO_EN_CV")
                )
        );

        List<RequisitoMatrizItemDTO> matriz = constructorMatriz.construirMatriz(
                List.of(req),
                respuestasIa,
                List.of(),
                Map.of(1, "Texto parcial"),
                true
        );

        assertEquals("REQUIERE_VERIFICACION", matriz.get(0).resultadoAnalisis());
        assertTrue(matriz.get(0).observacionEvidencia().contains("superó el límite de procesamiento íntegro"));
    }

    private RequisitoCargo mockReq(Long id, String nombre, boolean obligatorio, Integer meses) {
        RequisitoCargo r = mock(RequisitoCargo.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getNombreRequisito()).thenReturn(nombre);
        lenient().when(r.isObligatorio()).thenReturn(obligatorio);
        lenient().when(r.getExperienciaMinimaMeses()).thenReturn(meses);
        return r;
    }
}