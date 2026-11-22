package com.example.proyectoAquachile.Service.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP resiliente para Groq API con Structured Outputs (JSON Schema estricto),
 * validación de finish_reason, manejo explícito de errores y cero registro de secretos o CVs.
 */
@Service
public class GroqApiClientService {

    private static final int MAX_REINTENTOS_TRANSITORIOS = 1;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.api.model:openai/gpt-oss-120b}")
    private String apiModel;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqApiClientService(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    public String getModeloConfigurado() {
        return apiModel;
    }

    public void validarConfiguracion() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de IA no está configurado (falta GROQ_API_KEY en el entorno).");
        }
        if (apiModel == null || apiModel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se ha configurado un modelo explícito de IA (GROQ_API_MODEL).");
        }
    }

    public String ejecutarAnalisisEstructurado(String systemPrompt, String userPrompt) {
        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "analisis_cv_aquachile",
                        "strict", true,
                        "schema", construirSchemaAnalisisCv()
                )
        );
        return enviarConReintentoControlado(systemPrompt, userPrompt, responseFormat, 4096);
    }

    public String ejecutarPreguntasEstructuradas(String systemPrompt, String userPrompt) {
        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "preguntas_apoyo_aquachile",
                        "strict", true,
                        "schema", construirSchemaPreguntas()
                )
        );
        return enviarConReintentoControlado(systemPrompt, userPrompt, responseFormat, 2048);
    }

    private String enviarConReintentoControlado(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> responseFormat,
            int maxTokens) {

        validarConfiguracion();
        int intento = 0;

        while (true) {
            try {
                return ejecutarLlamadaUnica(systemPrompt, userPrompt, responseFormat, maxTokens);
            } catch (ResponseStatusException ex) {
                int code = ex.getStatusCode().value();
                boolean esTransitorio = (code == 429 || code == 502 || code == 503 || code == 504);
                if (!esTransitorio || intento >= MAX_REINTENTOS_TRANSITORIOS) {
                    throw ex;
                }
                intento++;
                try {
                    Thread.sleep(1200L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    private String ejecutarLlamadaUnica(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> responseFormat,
            int maxTokens) {

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", apiModel);
        requestBody.put("temperature", 0.1);
        requestBody.put("max_completion_tokens", maxTokens);
        requestBody.put("response_format", responseFormat);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        try {
            String rawResponse = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El proveedor de IA devolvió una respuesta HTTP vacía.");
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choice = root.path("choices").path(0);
            String finishReason = choice.path("finish_reason").asText("");
            if ("length".equalsIgnoreCase(finishReason)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "La respuesta del modelo de IA quedó truncada por límite de tokens.");
            }

            String texto = choice.path("message").path("content").asText("");
            if (texto.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El proveedor de IA no generó contenido estructurado.");
            }

            return extraerObjetoJson(texto);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (HttpClientErrorException.BadRequest e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El proveedor de IA rechazó la estructura de la solicitud (HTTP 400). Verifica compatibilidad del modelo configurado.");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Credenciales o permisos rechazados por el proveedor de IA.");
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El modelo de IA configurado ('" + apiModel + "') no está disponible en el proveedor.");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Se alcanzó temporalmente el límite de solicitudes de la API de IA. Intenta nuevamente en unos segundos.");
        } catch (HttpServerErrorException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El proveedor externo de IA presenta indisponibilidad temporal (HTTP " + e.getStatusCode().value() + ").");
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Tiempo de espera agotado al comunicarse con el servicio de IA.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error inesperado de comunicación con el servicio externo de IA.");
        }
    }

    private String extraerObjetoJson(String texto) {
        String limpio = texto.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
        int inicio = limpio.indexOf('{');
        int fin = limpio.lastIndexOf('}');
        if (inicio >= 0 && fin > inicio) {
            return limpio.substring(inicio, fin + 1);
        }
        return limpio;
    }

    private Map<String, Object> schemaEvidencia() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("pagina", "fragmento", "tipoAntecedente", "estadoVerificacion"),
                "properties", Map.of(
                        "pagina", Map.of("type", List.of("integer", "null")),
                        "fragmento", Map.of("type", "string"),
                        "tipoAntecedente", Map.of("type", "string"),
                        "estadoVerificacion", Map.of("type", "string")
                )
        );
    }

    private Map<String, Object> construirSchemaAnalisisCv() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("nombreDetectado", Map.of("type", "string"));
        props.put("tituloProfesional", Map.of("type", "string"));
        props.put("resumenProfesionalObjetivo", Map.of("type", "string"));

        props.put("formacionAcademica", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("titulo", "institucion", "periodo", "estadoAcademico", "evidencia", "estadoRevisionItem"),
                        "properties", Map.of(
                                "titulo", Map.of("type", "string"),
                                "institucion", Map.of("type", "string"),
                                "periodo", Map.of("type", "string"),
                                "estadoAcademico", Map.of("type", "string"),
                                "evidencia", schemaEvidencia(),
                                "estadoRevisionItem", Map.of("type", "string")
                        )
                )
        ));

        props.put("experienciasLaborales", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("cargo", "empresa", "periodo", "duracionCalculada", "funciones", "evidencia", "estadoRevisionItem"),
                        "properties", Map.of(
                                "cargo", Map.of("type", "string"),
                                "empresa", Map.of("type", "string"),
                                "periodo", Map.of("type", "string"),
                                "duracionCalculada", Map.of("type", "string"),
                                "funciones", Map.of("type", "array", "items", Map.of("type", "string")),
                                "evidencia", schemaEvidencia(),
                                "estadoRevisionItem", Map.of("type", "string")
                        )
                )
        ));

        props.put("habilidadesTecnicas", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("nombre", "contextoUso", "evidencia", "estadoRevisionItem"),
                        "properties", Map.of(
                                "nombre", Map.of("type", "string"),
                                "contextoUso", Map.of("type", "string"),
                                "evidencia", schemaEvidencia(),
                                "estadoRevisionItem", Map.of("type", "string")
                        )
                )
        ));

        props.put("competenciasDeclaradas", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("nombre", "evidencia", "estadoRevisionItem"),
                        "properties", Map.of(
                                "nombre", Map.of("type", "string"),
                                "evidencia", schemaEvidencia(),
                                "estadoRevisionItem", Map.of("type", "string")
                        )
                )
        ));

        props.put("certificacionesYLicencias", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("nombre", "entidadEmisora", "vigencia", "evidencia", "estadoRevisionItem"),
                        "properties", Map.of(
                                "nombre", Map.of("type", "string"),
                                "entidadEmisora", Map.of("type", "string"),
                                "vigencia", Map.of("type", "string"),
                                "evidencia", schemaEvidencia(),
                                "estadoRevisionItem", Map.of("type", "string")
                        )
                )
        ));

        props.put("matrizRequisitos", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("requisitoId", "requisito", "obligatorio", "resultadoAnalisis", "observacionEvidencia", "evidencia", "estadoRevisionItem"),
                        "properties", Map.of(
                                "requisitoId", Map.of("type", "integer"),
                                "requisito", Map.of("type", "string"),
                                "obligatorio", Map.of("type", "boolean"),
                                "resultadoAnalisis", Map.of("type", "string", "enum", List.of("ENCONTRADO", "REQUIERE_VERIFICACION", "NO_IDENTIFICADO_EN_CV")),
                                "observacionEvidencia", Map.of("type", "string"),
                                "evidencia", schemaEvidencia(),
                                "estadoRevisionItem", Map.of("type", "string")
                        )
                )
        ));

        props.put("datosFaltantesOAmbiguos", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("antecedente", "motivo", "requiereRevisionHumana"),
                        "properties", Map.of(
                                "antecedente", Map.of("type", "string"),
                                "motivo", Map.of("type", "string"),
                                "requiereRevisionHumana", Map.of("type", "boolean")
                        )
                )
        ));

        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of(
                        "nombreDetectado",
                        "tituloProfesional",
                        "resumenProfesionalObjetivo",
                        "formacionAcademica",
                        "experienciasLaborales",
                        "habilidadesTecnicas",
                        "competenciasDeclaradas",
                        "certificacionesYLicencias",
                        "matrizRequisitos",
                        "datosFaltantesOAmbiguos"
                ),
                "properties", props
        );
    }

    private Map<String, Object> construirSchemaPreguntas() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("preguntas"),
                "properties", Map.of(
                        "preguntas", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("pregunta", "objetivoVerificacion", "antecedenteRelacionado"),
                                        "properties", Map.of(
                                                "pregunta", Map.of("type", "string"),
                                                "objetivoVerificacion", Map.of("type", "string"),
                                                "antecedenteRelacionado", Map.of("type", "string")
                                        )
                                )
                        )
                )
        );
    }
}