package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CvAnalisisService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.api.model:llama-3.1-8b-instant}")
    private String apiModel;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CvAnalisisService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public AnalisisCvDTO analizarPdf(byte[] pdfBytes, String cargoPostulado) {
        try {
            String textoCv;
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                textoCv = stripper.getText(document);
            }

            if (textoCv == null || textoCv.isBlank()) {
                throw new RuntimeException("El PDF no contiene texto legible (posible documento escaneado como imagen).");
            }

            String prompt = """
                Eres un analista de selección de personal para AquaChile.
                Analiza el siguiente texto extraído de un currículum y responde ÚNICAMENTE con un objeto JSON válido con exactamente estas claves:
                {
                  "nombreDetectado": "string o null",
                  "tituloProfesional": "string",
                  "anosExperienciaEstimados": número entero o null,
                  "habilidadesClave": ["lista", "de", "strings"],
                  "certificacionesYLicencias": ["lista", "de", "strings"],
                  "ultimoCargo": "string o null",
                  "resumenEjecutivo": "resumen objetivo de máximo 3 líneas",
                  "observacionesReclutador": "observaciones o datos faltantes relevantes"
                }
                Cargo de referencia: %s.
                Si un dato no aparece en el CV, usa null o [], nunca inventes información.

                TEXTO DEL CV:
                %s
                """.formatted(cargoPostulado != null ? cargoPostulado : "General", textoCv);

            String rawResponse;
            try {
                rawResponse = llamarGroq(apiModel, prompt);
            } catch (HttpClientErrorException.NotFound e) {
                String modeloActivo = detectarModeloDisponible();
                rawResponse = llamarGroq(modeloActivo, prompt);
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            String jsonTexto = root.path("choices").get(0)
                    .path("message").path("content").asText()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("```$", "")
                    .trim();

            return objectMapper.readValue(jsonTexto, AnalisisCvDTO.class);

        } catch (Exception e) {
            throw new RuntimeException("Error al procesar el CV con IA: " + e.getMessage(), e);
        }
    }

    private String llamarGroq(String modelo, String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", modelo,
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        return restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    private String detectarModeloDisponible() {
        String modelsUrl = apiUrl.replace("/chat/completions", "/models");
        String modelsResponse = restClient.get()
                .uri(modelsUrl)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .body(String.class);

        JsonNode data = objectMapper.readTree(modelsResponse).path("data");
        List<String> disponibles = new ArrayList<>();
        for (JsonNode item : data) {
            String id = item.path("id").asText("");
            String lower = id.toLowerCase();
            if (!id.isBlank()
                    && !lower.contains("whisper")
                    && !lower.contains("tts")
                    && !lower.contains("guard")
                    && !lower.contains("orpheus")) {
                disponibles.add(id);
            }
        }

        if (disponibles.isEmpty()) {
            throw new RuntimeException("No se encontraron modelos de chat activos en tu cuenta de Groq.");
        }

        for (String preferido : List.of("llama-4", "gpt-oss-120b", "qwen3", "llama-3.3", "llama-3.1")) {
            for (String id : disponibles) {
                if (id.toLowerCase().contains(preferido)) {
                    return id;
                }
            }
        }
        return disponibles.get(0);
    }
}