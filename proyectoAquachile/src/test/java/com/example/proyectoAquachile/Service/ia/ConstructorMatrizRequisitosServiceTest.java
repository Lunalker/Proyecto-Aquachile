package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.ExperienciaItemDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.RequisitoMatrizItemDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConstructorMatrizRequisitosServiceTest {

    private final VerificadorEvidenciaService verificador = new VerificadorEvidenciaService();
    private final CalculadoraExperienciaService calculadora = new CalculadoraExperienciaService(
            Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneId.of("America/Santiago"))
    );
    private final ConstructorMatrizRequisitosService constructor =
            new ConstructorMatrizRequisitosService(verificador, calculadora);

    private RequisitoCargo crearReq(Long id, String nombre, boolean obligatorio, Integer mesesMin) {
        RequisitoCargo r = new RequisitoCargo();
        // Reflexión simple para fijar ID en pruebas unitarias sin depender de JPA
        try {
            var field = RequisitoCargo.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(r, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setCargo("Operador sala control");
        r.setNombreRequisito(nombre);
        r.setObligatorio(obligatorio);
        r.setExperienciaMinimaMeses(mesesMin);
        return r;
    }

    @Test
    void emparejaExactamentePorIdAunqueVenganDesordenados() {
        RequisitoCargo r1 = crearReq(10L, "Manejo de SCADA o telemetría", true, null);
        RequisitoCargo r2 = crearReq(20L, "Certificación prevención de riesgos", false, null);

        Map<Integer, String> paginas = Map.of(
                1, "Operación diaria de telemetría en centro de cultivo. Curso de prevención de riesgos aprobado."
        );

        List<RequisitoMatrizItemDTO> respuestaIaDesordenada = List.of(
                new RequisitoMatrizItemDTO(20L, "Nombre alterado por IA", false, "ENCONTRADO",
                        "Acredita curso", new EvidenciaDTO(1, "Curso de prevención de riesgos aprobado", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE"),
                new RequisitoMatrizItemDTO(10L, "Otro nombre", true, "ENCONTRADO",
                        "Cumple alternativa telemetría", new EvidenciaDTO(1, "Operación diaria de telemetría en centro de cultivo", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE")
        );

        List<RequisitoMatrizItemDTO> matriz = constructor.construirMatriz(
                List.of(r1, r2), respuestaIaDesordenada, List.of(), paginas, false
        );

        assertEquals(2, matriz.size());
        assertEquals(10L, matriz.get(0).requisitoId());
        assertEquals("Manejo de SCADA o telemetría", matriz.get(0).requisito());
        assertEquals("ENCONTRADO", matriz.get(0).resultadoAnalisis());
        assertTrue(matriz.get(0).obligatorio());

        assertEquals(20L, matriz.get(1).requisitoId());
        assertEquals("ENCONTRADO", matriz.get(1).resultadoAnalisis());
        assertFalse(matriz.get(1).obligatorio());
    }

    @Test
    void degradaAEncontradoConEvidenciaInvalida() {
        RequisitoCargo r1 = crearReq(10L, "Título técnico en automatización", true, null);
        Map<Integer, String> paginas = Map.of(1, "Enseñanza media completa.");

        List<RequisitoMatrizItemDTO> ia = List.of(
                new RequisitoMatrizItemDTO(10L, "Título técnico en automatización", true, "ENCONTRADO",
                        "Supuesto título", new EvidenciaDTO(1, "Técnico Superior en Automatización", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE")
        );

        List<RequisitoMatrizItemDTO> matriz = constructor.construirMatriz(
                List.of(r1), ia, List.of(), paginas, false
        );

        assertEquals("REQUIERE_VERIFICACION", matriz.get(0).resultadoAnalisis());
        assertEquals("NO_VERIFICABLE_EN_TEXTO", matriz.get(0).evidencia().estadoVerificacion());
    }

    @Test
    void manejaIdInexistenteIdDuplicadoYRequisitoOmitidoSinAsignacionArbitraria() {
        RequisitoCargo r1 = crearReq(10L, "Requisito A", true, null);
        RequisitoCargo r2 = crearReq(20L, "Requisito B omitido", true, null);

        Map<Integer, String> paginas = Map.of(1, "Texto de prueba en CV.");

        List<RequisitoMatrizItemDTO> ia = List.of(
                new RequisitoMatrizItemDTO(999L, "ID Fantasma", true, "ENCONTRADO",
                        "Obs", new EvidenciaDTO(1, "Texto de prueba en CV", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE"),
                new RequisitoMatrizItemDTO(10L, "Requisito A", true, "ENCONTRADO",
                        "Obs 1", new EvidenciaDTO(1, "Texto de prueba en CV", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE"),
                new RequisitoMatrizItemDTO(10L, "Requisito A", true, "NO_IDENTIFICADO_EN_CV",
                        "Obs 2", new EvidenciaDTO(null, "", "REQUISITO_CARGO", "NO_IDENTIFICADO_EN_CV"), "PENDIENTE")
        );

        List<RequisitoMatrizItemDTO> matriz = constructor.construirMatriz(
                List.of(r1, r2), ia, List.of(), paginas, false
        );

        assertEquals(2, matriz.size());
        // r1 duplicado -> REQUIERE_VERIFICACION por inconsistencia
        assertEquals("REQUIERE_VERIFICACION", matriz.get(0).resultadoAnalisis());
        assertTrue(matriz.get(0).observacionEvidencia().contains("múltiples evaluaciones"));
        // r2 omitido por la IA -> REQUIERE_VERIFICACION (no NO_IDENTIFICADO_EN_CV)
        assertEquals("REQUIERE_VERIFICACION", matriz.get(1).resultadoAnalisis());
        assertTrue(matriz.get(1).observacionEvidencia().contains("omitió evaluar"));
    }

    @Test
    void degradaSiExperienciaComprobableNoAlcanzaMinimoExigido() {
        RequisitoCargo r1 = crearReq(10L, "Experiencia en sala de control", true, 24);
        Map<Integer, String> paginas = Map.of(1, "Operador sala de control entre Ene 2025 y Dic 2025.");

        List<ExperienciaItemDTO> exps = List.of(
                new ExperienciaItemDTO("Operador sala de control", "AquaSur", "Ene 2025 - Dic 2025", "1 año",
                        List.of("Monitoreo"), new EvidenciaDTO(1, "Operador sala de control entre Ene 2025 y Dic 2025", "EXPERIENCIA", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE")
        );

        List<RequisitoMatrizItemDTO> ia = List.of(
                new RequisitoMatrizItemDTO(10L, "Experiencia en sala de control", true, "ENCONTRADO",
                        "Trabajó en sala de control", new EvidenciaDTO(1, "Operador sala de control entre Ene 2025 y Dic 2025", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE")
        );

        List<RequisitoMatrizItemDTO> matriz = constructor.construirMatriz(
                List.of(r1), ia, exps, paginas, false
        );

        assertEquals("REQUIERE_VERIFICACION", matriz.get(0).resultadoAnalisis());
        assertTrue(matriz.get(0).observacionEvidencia().contains("1 año frente a un mínimo de 2 años"));
    }
}