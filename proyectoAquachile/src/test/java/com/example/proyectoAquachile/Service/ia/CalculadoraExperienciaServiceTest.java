package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.ExperienciaItemDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import com.example.proyectoAquachile.Service.ia.CalculadoraExperienciaService.ResultadoEvaluacionExperiencia;
import com.example.proyectoAquachile.Service.ia.CalculadoraExperienciaService.ResumenCronologicoGlobal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CalculadoraExperienciaServiceTest {

    private CalculadoraExperienciaService calculadora;

    @BeforeEach
    void setUp() {
        // Fecha fija: 15 de noviembre de 2026 en zona America/Santiago
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-11-15T15:00:00Z"),
                CalculadoraExperienciaService.ZONA_CHILE
        );
        calculadora = new CalculadoraExperienciaService(fixedClock);
    }

    @Test
    void calculaDuracionInclusivaYVigenteConClockControlado() {
        assertEquals("1 año y 2 meses", calculadora.calcularDuracionIndividual("Ene 2024 - Feb 2025", null));
        assertEquals("11 meses (vigente a la fecha)", calculadora.calcularDuracionIndividual("Enero 2026 - Actualidad", null));
    }

    @Test
    void noInventaMesesCuandoElPeriodoSoloIndicaAnios() {
        String duracion = calculadora.calcularDuracionIndividual("2022 - 2023", "2 años");
        assertTrue(duracion.contains("requiere confirmación de meses exactos"));
    }

    @Test
    void fusionaPeriodosSimultaneosSinDuplicarTiempoCronologico() {
        List<ExperienciaItemDTO> experiencias = List.of(
                exp("Jefe de Centro", "AquaChile", "Ene 2023 - Dic 2024"), // 24 meses
                exp("Asesor Técnico", "Consultora Sur", "Jun 2023 - Jun 2024") // 13 meses superpuestos
        );

        ResumenCronologicoGlobal resumen = calculadora.calcularExperienciaCronologicaGlobal(experiencias);

        assertTrue(resumen.tieneSolapamientos());
        assertEquals(24, resumen.mesesCronologicosTotales());
        assertTrue(resumen.notaSolapamiento().contains("3 años y 1 mes"));
        assertTrue(resumen.notaSolapamiento().contains("2 años"));
    }

    @Test
    void evaluaCumplimientoMinimoUsandoIndicesRelacionadosYExcluyendoCargosNoAfines() {
        RequisitoCargo req = mock(RequisitoCargo.class);
        lenient().when(req.getNombreRequisito()).thenReturn("Experiencia en plantas de proceso");
        lenient().when(req.getDescripcion()).thenReturn("Al menos 12 meses en planta");

        List<ExperienciaItemDTO> experiencias = List.of(
                exp("Vendedor Retail", "Tienda Centro", "Ene 2020 - Dic 2023"), // índice 0: 48 meses no afines
                exp("Supervisor de Planta", "Salmones Austral", "Mar 2025 - Oct 2025") // índice 1: 8 meses afines
        );

        ResultadoEvaluacionExperiencia eval = calculadora.evaluarCumplimientoMinimo(
                experiencias,
                req,
                12,
                List.of(1)
        );

        assertFalse(eval.cumpleMinimoComprobable());
        assertEquals(8, eval.mesesComprobables());
    }

    private ExperienciaItemDTO exp(String cargo, String empresa, String periodo) {
        return new ExperienciaItemDTO(
                cargo,
                empresa,
                periodo,
                "",
                List.of(),
                new EvidenciaDTO(1, cargo, "EXPERIENCIA", "VERIFICADO_EN_DOCUMENTO"),
                "PENDIENTE"
        );
    }
}