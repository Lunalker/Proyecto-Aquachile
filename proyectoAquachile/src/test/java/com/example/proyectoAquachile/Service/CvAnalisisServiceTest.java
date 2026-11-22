package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import com.example.proyectoAquachile.Service.ia.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CvAnalisisServiceTest {

    private ExtractorPdfService extractorPdf;
    private GroqApiClientService groqClient;
    private CvAnalisisService cvAnalisisService;

    @BeforeEach
    void setUp() {
        extractorPdf = mock(ExtractorPdfService.class);
        groqClient = mock(GroqApiClientService.class);

        AnonimizadorPiiService anonimizador = new AnonimizadorPiiService();
        ConstructorPromptsIaService prompts = new ConstructorPromptsIaService();
        VerificadorEvidenciaService verificador = new VerificadorEvidenciaService();
        CalculadoraExperienciaService calculadora = new CalculadoraExperienciaService(
                Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), CalculadoraExperienciaService.ZONA_CHILE)
        );
        ConstructorMatrizRequisitosService matrizService = new ConstructorMatrizRequisitosService(verificador, calculadora);

        cvAnalisisService = new CvAnalisisService(
                extractorPdf,
                anonimizador,
                groqClient,
                prompts,
                verificador,
                calculadora,
                matrizService,
                new ObjectMapper()
        );
    }

    @Test
    void anonimizaPiiYConsolidaMultiplesBloquesDesplazandoIndicesDeExperiencia() {
        byte[] dummyPdf = "%PDF-1.7 dummy".getBytes();

        Map<Integer, String> paginas = new LinkedHashMap<>();
        paginas.put(1, "Juan Pérez González | RUT: 18.456.789-K | Tel: +56 9 8765 4321\nAnalista de Datos Jr en Empresa A (Ene 2024 - Dic 2024).");
        paginas.put(2, "Ingeniero de Procesos en Empresa B (Ene 2025 - Dic 2025) con experiencia en control de producción.");

        Map<Integer, String> bloque1 = Map.of(1, "[CANDIDATO] | RUT: [RUT_OMITIDO] | Tel: [TELEFONO_OMITIDO]\nAnalista de Datos Jr en Empresa A (Ene 2024 - Dic 2024).");
        Map<Integer, String> bloque2 = Map.of(2, "Ingeniero de Procesos en Empresa B (Ene 2025 - Dic 2025) con experiencia en control de producción.");

        when(extractorPdf.extraerDocumento(dummyPdf))
                .thenReturn(new ExtractorPdfService.DocumentoExtraido(paginas, false, List.of()));
        when(extractorPdf.dividirEnBloques(anyMap()))
                .thenReturn(List.of(bloque1, bloque2));

        RequisitoCargo reqExp = mock(RequisitoCargo.class);
        lenient().when(reqExp.getId()).thenReturn(100L);
        lenient().when(reqExp.getNombreRequisito()).thenReturn("Experiencia en control de producción");
        lenient().when(reqExp.isObligatorio()).thenReturn(true);
        lenient().when(reqExp.getExperienciaMinimaMeses()).thenReturn(24);

        String jsonBloque1 = """
                {
                  "nombreDetectado": "[CANDIDATO]",
                  "tituloProfesional": "Ingeniero Civil Industrial",
                  "resumenProfesionalObjetivo": "Profesional con experiencia en análisis.",
                  "formacionAcademica": [],
                  "experienciasLaborales": [
                    {
                      "cargo": "Analista de Datos Jr",
                      "empresa": "Empresa A",
                      "periodo": "Ene 2024 - Dic 2024",
                      "duracionCalculada": "1 año",
                      "funciones": ["Análisis de datos"],
                      "evidencia": {
                        "pagina": 1,
                        "fragmento": "Analista de Datos Jr en Empresa A",
                        "tipoAntecedente": "EXPERIENCIA",
                        "estadoVerificacion": "PENDIENTE"
                      },
                      "estadoRevisionItem": "PENDIENTE"
                    }
                  ],
                  "habilidadesTecnicas": [],
                  "competenciasDeclaradas": [],
                  "certificacionesYLicencias": [],
                  "matrizRequisitos": [
                    {
                      "requisitoId": 100,
                      "requisito": "Experiencia en control de producción",
                      "obligatorio": true,
                      "resultadoAnalisis": "REQUIERE_VERIFICACION",
                      "condicionesEvaluadas": ["experiencia afín"],
                      "experienciasRelacionadasIndices": [0],
                      "observacionEvidencia": "Experiencia parcial en bloque 1",
                      "evidencia": {
                        "pagina": 1,
                        "fragmento": "Analista de Datos Jr en Empresa A",
                        "tipoAntecedente": "REQUISITO_CARGO",
                        "estadoVerificacion": "PENDIENTE"
                      }
                    }
                  ],
                  "datosFaltantesOAmbiguos": []
                }
                """;

        String jsonBloque2 = """
                {
                  "nombreDetectado": "NO_IDENTIFICADO_EN_CV",
                  "tituloProfesional": "",
                  "resumenProfesionalObjetivo": "",
                  "formacionAcademica": [],
                  "experienciasLaborales": [
                    {
                      "cargo": "Ingeniero de Procesos",
                      "empresa": "Empresa B",
                      "periodo": "Ene 2025 - Dic 2025",
                      "duracionCalculada": "1 año",
                      "funciones": ["Control de producción"],
                      "evidencia": {
                        "pagina": 2,
                        "fragmento": "Ingeniero de Procesos en Empresa B",
                        "tipoAntecedente": "EXPERIENCIA",
                        "estadoVerificacion": "PENDIENTE"
                      },
                      "estadoRevisionItem": "PENDIENTE"
                    }
                  ],
                  "habilidadesTecnicas": [],
                  "competenciasDeclaradas": [],
                  "certificacionesYLicencias": [],
                  "matrizRequisitos": [
                    {
                      "requisitoId": 100,
                      "requisito": "Experiencia en control de producción",
                      "obligatorio": true,
                      "resultadoAnalisis": "ENCONTRADO",
                      "condicionesEvaluadas": ["control de producción"],
                      "experienciasRelacionadasIndices": [0],
                      "observacionEvidencia": "Experiencia comprobada en control de producción",
                      "evidencia": {
                        "pagina": 2,
                        "fragmento": "experiencia en control de producción",
                        "tipoAntecedente": "REQUISITO_CARGO",
                        "estadoVerificacion": "PENDIENTE"
                      }
                    }
                  ],
                  "datosFaltantesOAmbiguos": []
                }
                """;

        when(groqClient.ejecutarAnalisisEstructurado(anyString(), anyString()))
                .thenReturn(jsonBloque1, jsonBloque2);

        AnalisisCvDTO resultado = cvAnalisisService.analizarPdf(
                dummyPdf,
                "Juan Pérez González",
                "Jefe de Producción",
                "Operaciones",
                List.of(reqExp)
        );

        // Verifica que nunca se envió PII al prompt del LLM
        ArgumentCaptor<String> userPrompts = ArgumentCaptor.forClass(String.class);
        verify(groqClient, times(2)).ejecutarAnalisisEstructurado(anyString(), userPrompts.capture());
        for (String enviado : userPrompts.getAllValues()) {
            assertFalse(enviado.contains("18.456.789-K"));
            assertFalse(enviado.contains("8765 4321"));
            assertFalse(enviado.contains("Juan Pérez González"));
        }

        // Verifica restitución del nombre interno y suma cronológica de ambos bloques (12 + 12 = 24 meses)
        assertEquals("Juan Pérez González", resultado.nombreDetectado());
        assertEquals(2, resultado.experienciasLaborales().size());
        assertEquals(1, resultado.matrizRequisitos().size());
        assertEquals("ENCONTRADO", resultado.matrizRequisitos().get(0).resultadoAnalisis());
        assertTrue(resultado.matrizRequisitos().get(0).observacionEvidencia().contains("2 años frente a un mínimo de 2 años"));
    }
}