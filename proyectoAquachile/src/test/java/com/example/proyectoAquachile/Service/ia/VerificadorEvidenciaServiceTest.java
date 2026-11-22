package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import com.example.proyectoAquachile.Service.ia.VerificadorEvidenciaService.ResultadoVerificacionEvidencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerificadorEvidenciaServiceTest {

    private VerificadorEvidenciaService verificador;

    @BeforeEach
    void setUp() {
        verificador = new VerificadorEvidenciaService();
    }

    @Test
    void verificaCitaLiteralEnPaginaIndicadaConOffsetsExactos() {
        Map<Integer, String> paginas = Map.of(
                1, "Experiencia en supervisión de centros de cultivo y control de parámetros en piscicultura.",
                2, "Certificación vigente en prevención de riesgos."
        );

        EvidenciaDTO cruda = new EvidenciaDTO(
                1,
                "supervisión de centros de cultivo",
                "EXPERIENCIA",
                "PENDIENTE"
        );

        ResultadoVerificacionEvidencia res = verificador.verificar(
                cruda,
                "EXPERIENCIA",
                "Experiencia en centros de cultivo",
                paginas
        );

        assertTrue(res.verificada());
        assertEquals(VerificadorEvidenciaService.ESTADO_VERIFICADO, res.evidencia().estadoVerificacion());
        assertEquals(1, res.evidencia().pagina());
        assertNotNull(res.inicioCaracter());
        assertNotNull(res.finCaracter());
        assertTrue(res.finCaracter() > res.inicioCaracter());
    }

    @Test
    void corrigePaginaAutomaticamenteCuandoLaCitaExisteEnUnaSolaPaginaDistinta() {
        Map<Integer, String> paginas = Map.of(
                1, "Resumen profesional: Ingeniero Acuícola.",
                2, "Manejo avanzado de tablas dinámicas en Excel para control de producción."
        );

        EvidenciaDTO cruda = new EvidenciaDTO(
                1,
                "tablas dinámicas en Excel",
                "HABILIDAD_TECNICA",
                "PENDIENTE"
        );

        ResultadoVerificacionEvidencia res = verificador.verificar(
                cruda,
                "HABILIDAD_TECNICA",
                "Dominio de Excel",
                paginas
        );

        assertTrue(res.verificada());
        assertEquals(2, res.evidencia().pagina());
        assertEquals(VerificadorEvidenciaService.ESTADO_VERIFICADO, res.evidencia().estadoVerificacion());
    }

    @Test
    void rechazaPalabrasSueltasDispersasNoContiguas() {
        Map<Integer, String> paginas = Map.of(
                1, "Operador de planta en Puerto Montt. Uso básico de computador en oficina."
        );

        EvidenciaDTO cruda = new EvidenciaDTO(
                1,
                "Operador SCADA avanzado",
                "REQUISITO_CARGO",
                "PENDIENTE"
        );

        ResultadoVerificacionEvidencia res = verificador.verificar(
                cruda,
                "REQUISITO_CARGO",
                "Operador SCADA",
                paginas
        );

        assertFalse(res.verificada());
        assertEquals(VerificadorEvidenciaService.ESTADO_NO_VERIFICABLE, res.evidencia().estadoVerificacion());
        assertEquals("REQUIERE_VERIFICACION", verificador.aplicarReglaDegradacionEstado("ENCONTRADO", res));
    }

    @Test
    void marcaAmbiguoCuandoCitaApareceEnMultiplesPaginasYLaIndicadaEsIncorrecta() {
        Map<Integer, String> paginas = Map.of(
                1, "Control de calidad en línea de proceso.",
                2, "Control de calidad en línea de proceso."
        );

        EvidenciaDTO cruda = new EvidenciaDTO(
                99,
                "Control de calidad en línea de proceso",
                "REQUISITO_CARGO",
                "PENDIENTE"
        );

        ResultadoVerificacionEvidencia res = verificador.verificar(
                cruda,
                "REQUISITO_CARGO",
                "Control de calidad",
                paginas
        );

        assertFalse(res.verificada());
        assertEquals(VerificadorEvidenciaService.ESTADO_AMBIGUO_PAGINAS, res.evidencia().estadoVerificacion());
    }

    @Test
    void detectaCitaLiteralSinCoherenciaContextualConElAntecedente() {
        Map<Integer, String> paginas = Map.of(
                1, "Licencia de conducir clase B vigente desde 2020."
        );

        EvidenciaDTO cruda = new EvidenciaDTO(
                1,
                "Licencia de conducir clase B",
                "REQUISITO_CARGO",
                "PENDIENTE"
        );

        ResultadoVerificacionEvidencia res = verificador.verificar(
                cruda,
                "REQUISITO_CARGO",
                "Certificación internacional HACCP",
                paginas
        );

        assertFalse(res.verificada());
        assertEquals(VerificadorEvidenciaService.ESTADO_SIN_CONTEXTO, res.evidencia().estadoVerificacion());
    }
}