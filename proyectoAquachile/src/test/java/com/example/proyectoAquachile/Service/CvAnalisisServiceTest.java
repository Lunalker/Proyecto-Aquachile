package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Service.ia.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CvAnalisisServiceTest {

    @Test
    void instanciaOrquestadorCorrectamente() {
        VerificadorEvidenciaService verificador = new VerificadorEvidenciaService();
        CalculadoraExperienciaService calc = new CalculadoraExperienciaService();
        CvAnalisisService service = new CvAnalisisService(
                new ExtractorPdfService(),
                new AnonimizadorPiiService(),
                new GroqApiClientService(new ObjectMapper()),
                new ConstructorPromptsIaService(),
                verificador,
                calc,
                new ConstructorMatrizRequisitosService(verificador, calc),
                new ObjectMapper()
        );
        assertNotNull(service);
    }
}