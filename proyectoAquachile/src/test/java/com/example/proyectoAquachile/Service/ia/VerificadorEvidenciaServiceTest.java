package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerificadorEvidenciaServiceTest {

    private final VerificadorEvidenciaService verificador = new VerificadorEvidenciaService();

    @Test
    void citaLiteralEncontradaEnPaginaCorrecta() {
        Map<Integer, String> paginas = Map.of(
                1, "Operador de Sala de Control en Planta Quellón con monitoreo continuo de sistemas SCADA."
        );
        EvidenciaDTO ev = new EvidenciaDTO(1, "monitoreo continuo de sistemas SCADA", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "REQUISITO_CARGO", "Sistemas SCADA", paginas);

        assertEquals("VERIFICADO_EN_DOCUMENTO", res.estadoVerificacion());
        assertEquals(1, res.pagina());
    }

    @Test
    void citaInexistenteEsRechazada() {
        Map<Integer, String> paginas = Map.of(1, "Experiencia administrativa en bodega y despacho.");
        EvidenciaDTO ev = new EvidenciaDTO(1, "Certificación internacional ISO 22000 vigente", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "REQUISITO_CARGO", "ISO 22000", paginas);

        assertEquals("NO_VERIFICABLE_EN_TEXTO", res.estadoVerificacion());
    }

    @Test
    void citaConPalabrasDispersasNoSeAceptaComoVerificada() {
        Map<Integer, String> paginas = Map.of(
                1, "Operador en turnos rotativos. Control de inventario semanal. Sistemas eléctricos básicos."
        );
        EvidenciaDTO ev = new EvidenciaDTO(1, "Operador de control de sistemas", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "REQUISITO_CARGO", "Operador control", paginas);

        assertEquals("NO_VERIFICABLE_EN_TEXTO", res.estadoVerificacion());
    }

    @Test
    void citaEnPaginaIncorrectaSeResuelveSiEsUnicaEnElDocumento() {
        Map<Integer, String> paginas = Map.of(
                1, "Resumen profesional y formación técnica superior.",
                2, "Licencia de conducir clase B y D al día."
        );
        EvidenciaDTO ev = new EvidenciaDTO(1, "Licencia de conducir clase B y D al día", "CERTIFICACION", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "CERTIFICACION", "Licencia clase D", paginas);

        assertEquals("VERIFICADO_EN_DOCUMENTO", res.estadoVerificacion());
        assertEquals(2, res.pagina());
    }

    @Test
    void citaDuplicadaEnVariasPaginasConPaginaInvalidaSolicitaRevision() {
        Map<Integer, String> paginas = Map.of(
                1, "Manejo avanzado de Excel y reportes operativos.",
                2, "Manejo avanzado de Excel y reportes operativos."
        );
        EvidenciaDTO ev = new EvidenciaDTO(99, "Manejo avanzado de Excel y reportes operativos", "HABILIDAD_TECNICA", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "HABILIDAD_TECNICA", "Excel", paginas);

        assertEquals("NO_VERIFICABLE_EN_TEXTO", res.estadoVerificacion());
    }

    @Test
    void citaSinRelacionSemanticaConElAntecedenteNoRespalda() {
        Map<Integer, String> paginas = Map.of(
                1, "Participación en taller de cocina saludable y repostería básica."
        );
        EvidenciaDTO ev = new EvidenciaDTO(1, "taller de cocina saludable y repostería básica", "REQUISITO_CARGO", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "REQUISITO_CARGO", "Mantenimiento electromecánico industrial", paginas);

        assertEquals("NO_VERIFICABLE_EN_TEXTO", res.estadoVerificacion());
    }

    @Test
    void fragmentosConSaltosDeLineaYGuionesPdfSeNormalizan() {
        Map<Integer, String> paginas = Map.of(
                1, "Supervisión de procesos de alimenta-\nción automática en centros de cultivo."
        );
        EvidenciaDTO ev = new EvidenciaDTO(1, "procesos de alimentación automática en centros de cultivo", "EXPERIENCIA", "VERIFICADO_EN_DOCUMENTO");

        EvidenciaDTO res = verificador.verificarEvidenciaContextual(ev, "EXPERIENCIA", "Alimentación automática", paginas);

        assertEquals("VERIFICADO_EN_DOCUMENTO", res.estadoVerificacion());
    }
}