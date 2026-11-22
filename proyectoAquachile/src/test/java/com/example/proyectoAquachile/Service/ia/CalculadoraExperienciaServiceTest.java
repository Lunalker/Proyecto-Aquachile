package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.ExperienciaItemDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalculadoraExperienciaServiceTest {

    private final Clock clockFijo = Clock.fixed(
            Instant.parse("2026-11-20T12:00:00Z"),
            ZoneId.of("America/Santiago")
    );
    private final CalculadoraExperienciaService calc = new CalculadoraExperienciaService(clockFijo);

    private ExperienciaItemDTO exp(String cargo, String empresa, String periodo) {
        return new ExperienciaItemDTO(cargo, empresa, periodo, "", List.of(),
                new EvidenciaDTO(1, cargo, "EXPERIENCIA", "VERIFICADO_EN_DOCUMENTO"), "PENDIENTE");
    }

    @Test
    void calculaPeriodoCompletoYVigenteConClockChile() {
        var pCompleto = calc.parsearPeriodo("Ene 2023 - Dic 2024");
        assertTrue(pCompleto.exacto());
        assertEquals(24, pCompleto.mesesInclusivos());

        var pVigente = calc.parsearPeriodo("Sep 2026 - Actualidad");
        assertTrue(pVigente.exacto());
        assertTrue(pVigente.vigente());
        assertEquals(3, pVigente.mesesInclusivos()); // Sep, Oct, Nov 2026
    }

    @Test
    void noDuplicaPeriodosSimultaneosNiSuperpuestos() {
        List<ExperienciaItemDTO> simultaneos = List.of(
                exp("Operador Sala Control", "Planta A", "Ene 2024 - Dic 2024"),
                exp("Asistente Telemetría", "Planta B", "Ene 2024 - Dic 2024")
        );

        var resumen = calc.calcularExperienciaCronologicaGlobal(simultaneos);
        assertEquals(12, resumen.mesesCronologicosTotales());
        assertTrue(resumen.tieneSolapamientos());
    }

    @Test
    void noInventaMesesEnFechasIncompletasYDetectaFechasInvertidas() {
        var soloAnios = calc.parsearPeriodo("2022 - 2023");
        assertFalse(soloAnios.exacto());
        assertEquals(0, soloAnios.mesesInclusivos());

        var invertido = calc.parsearPeriodo("Mar 2024 - Ene 2022");
        assertTrue(invertido.inconsistente());
        assertTrue(calc.calcularDuracionIndividual("Mar 2024 - Ene 2022", "").contains("inconsistente"));
    }

    @Test
    void evaluaExperienciaMinimaAcreditadaYNoAcreditada() {
        RequisitoCargo req = new RequisitoCargo();
        req.setNombreRequisito("Experiencia en mantenimiento industrial");
        req.setExperienciaMinimaMeses(18);

        var insuficiente = calc.evaluarCumplimientoMinimo(
                List.of(exp("Técnico mantenimiento industrial", "Empresa X", "Ene 2025 - Dic 2025")),
                req, 18
        );
        assertFalse(insuficiente.cumpleMinimoComprobable());
        assertEquals(12, insuficiente.mesesComprobables());

        var suficiente = calc.evaluarCumplimientoMinimo(
                List.of(exp("Técnico mantenimiento industrial", "Empresa X", "Ene 2024 - Jun 2025")),
                req, 18
        );
        assertTrue(suficiente.cumpleMinimoComprobable());
        assertEquals(18, suficiente.mesesComprobables());
    }
}