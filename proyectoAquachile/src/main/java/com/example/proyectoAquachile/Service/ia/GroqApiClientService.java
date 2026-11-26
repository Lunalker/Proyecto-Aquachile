package com.example.proyectoAquachile.Service.ia;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente HTTP resiliente multi-proveedor (Google Gemini / Groq / OpenAI-compatible)
 * con autodescubrimiento de modelos activos (/models), Structured Outputs (JSON Schema estricto),
 * backoff con Retry-After y fallback automático entre modelos y proveedores.
 */
@Service
public class GroqApiClientService {

    private static final long MAX_ESPERA_RETRY_AFTER_MS = 15_000L;

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.url:https://generativelanguage.googleapis.com/v1beta/openai/chat/completions}")
    private String apiUrl;

    @Value("${groq.api.model:gemini-3-flash-preview,gemini-2.5-flash-lite}")
    private String apiModel;

    @Value("${groq.fallback.key:}")
    private String fallbackApiKey;

    @Value("${groq.fallback.url:https://api.groq.com/openai/v1/chat/completions}")
    private String fallbackApiUrl;

    @Value("${groq.fallback.model:meta-llama/llama-4-scout-17b-16e-instruct,llama-3.3-70b-versatile}")
    private String fallbackApiModel;

    @Value("${aquachile.ia.groq.max-completion-tokens:6144}")
    private int maxCompletionTokens;

    @Value("${aquachile.ia.groq.max-reintentos:2}")
    private int maxReintentosPorProveedor;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, List<String>> modelosDescubiertosPorHost = new ConcurrentHashMap<>();
    private volatile String ultimoModeloEfectivo;

    private record ProveedorConfig(String url, String key, String model) {}

    public GroqApiClientService(ObjectMapper objectMapper) {
        this(objectMapper, 10_000, 60_000);
    }

    @Autowired
    public GroqApiClientService(
            ObjectMapper objectMapper,
            @Value("${aquachile.ia.groq.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${aquachile.ia.groq.read-timeout-ms:60000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1_000, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(5_000, readTimeoutMs)));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void registrarProveedoresActivos() {
        String clavePrincipal = limpiarClave(apiKey);
        if (!clavePrincipal.isBlank() && apiUrl != null && !apiUrl.isBlank()) {
            descubrirModelosEnProveedor(apiUrl.trim(), clavePrincipal);
        }

        List<ProveedorConfig> activos = construirCadenaProveedores();
        if (activos.isEmpty()) {
            System.err.println("[IA CONFIG] ADVERTENCIA: No se detectaron claves API válidas (GEMINI_API_KEY ni GROQ_API_KEY).");
            return;
        }
        for (int i = 0; i < activos.size(); i++) {
            ProveedorConfig p = activos.get(i);
            String rol = (i == 0) ? "PRINCIPAL" : "RESPALDO #" + i;
            System.out.println("[IA CONFIG] " + rol + " -> modelo: " + p.model() + " | host: " + extraerHost(p.url()));
        }
    }

    public String getModeloConfigurado() {
        if (ultimoModeloEfectivo != null && !ultimoModeloEfectivo.isBlank()) {
            return ultimoModeloEfectivo;
        }
        List<ProveedorConfig> activos = construirCadenaProveedores();
        if (!activos.isEmpty()) {
            return activos.get(0).model();
        }
        return apiModel;
    }

    public void validarConfiguracion() {
        if (construirCadenaProveedores().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de IA no está configurado (falta GEMINI_API_KEY o GROQ_API_KEY en .env).");
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
        int limiteTokens = Math.max(4096, maxCompletionTokens);
        return enviarConFallbackYReintentos(systemPrompt, userPrompt, responseFormat, limiteTokens);
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
        return enviarConFallbackYReintentos(systemPrompt, userPrompt, responseFormat, 2048);
    }

    private List<ProveedorConfig> construirCadenaProveedores() {
        List<ProveedorConfig> proveedores = new ArrayList<>();
        String clavePrincipal = limpiarClave(apiKey);
        if (!clavePrincipal.isBlank() && apiUrl != null && !apiUrl.isBlank()) {
            String urlLimpia = apiUrl.trim();
            List<String> descubiertos = modelosDescubiertosPorHost.getOrDefault(extraerHost(urlLimpia), List.of());
            for (String modelo : descubiertos) {
                agregarModeloUnico(proveedores, urlLimpia, clavePrincipal, modelo);
            }
            if (apiModel != null && !apiModel.isBlank()) {
                agregarModelosCsv(proveedores, urlLimpia, clavePrincipal, apiModel);
            }
        }

        String claveRespaldo = limpiarClave(fallbackApiKey);
        if (claveRespaldo.isBlank() && !clavePrincipal.isBlank() && mismoHost(apiUrl, fallbackApiUrl)) {
            claveRespaldo = clavePrincipal;
        }

        if (!claveRespaldo.isBlank()
                && fallbackApiUrl != null && !fallbackApiUrl.isBlank()
                && fallbackApiModel != null && !fallbackApiModel.isBlank()) {
            agregarModelosCsv(proveedores, fallbackApiUrl.trim(), claveRespaldo, fallbackApiModel);
        }
        return proveedores;
    }

    private void agregarModelosCsv(List<ProveedorConfig> destino, String url, String key, String modelosCsv) {
        for (String rawModel : modelosCsv.split(",")) {
            agregarModeloUnico(destino, url, key, rawModel);
        }
    }

    private void agregarModeloUnico(List<ProveedorConfig> destino, String url, String key, String rawModel) {
        if (rawModel == null) return;
        String modelo = rawModel.trim();
        if (modelo.isEmpty()) return;
        ProveedorConfig candidato = new ProveedorConfig(url, key, modelo);
        boolean existe = destino.stream().anyMatch(p ->
                p.url().equalsIgnoreCase(candidato.url()) && p.model().equalsIgnoreCase(candidato.model()));
        if (!existe) {
            destino.add(candidato);
        }
    }

    private void descubrirModelosEnProveedor(String chatCompletionsUrl, String key) {
        String host = extraerHost(chatCompletionsUrl);
        String modelsUrl = chatCompletionsUrl.endsWith("/chat/completions")
                ? chatCompletionsUrl.substring(0, chatCompletionsUrl.length() - "/chat/completions".length()) + "/models"
                : null;
        if (modelsUrl == null) {
            return;
        }

        try {
            String raw = restClient.get()
                    .uri(modelsUrl)
                    .header("Authorization", "Bearer " + key)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                return;
            }

            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return;
            }

            boolean esGoogle = host.contains("googleapis.com");
            List<String> candidatos = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText("").trim();
                if (id.startsWith("models/")) {
                    id = id.substring("models/".length());
                }
                if (esModeloCompatibleJson(id, esGoogle)) {
                    candidatos.add(id);
                }
            }

            candidatos.sort(Comparator.comparingInt(this::prioridadModelo).thenComparing(Comparator.reverseOrder()));
            if (!candidatos.isEmpty()) {
                modelosDescubiertosPorHost.put(host, List.copyOf(candidatos));
                System.out.println("[IA DISCOVERY] Modelos habilitados en " + host + ": " + candidatos);
            }
        } catch (Exception e) {
            System.err.println("[IA DISCOVERY] No se pudo consultar /models en " + host + ": " + e.getMessage());
        }
    }

    private boolean esModeloCompatibleJson(String id, boolean esGoogle) {
        if (id == null || id.isBlank()) return false;
        String lower = id.toLowerCase(Locale.ROOT);
        if (esGoogle && !lower.contains("gemini")) {
            return false;
        }
        String[] excluidos = {
                "embedding", "imagen", "veo", "tts", "audio", "aqa", "lyria",
                "robotics", "computer-use", "deep-research", "learnlm", "gemma",
                "vision", "whisper", "guard"
        };
        for (String ex : excluidos) {
            if (lower.contains(ex)) {
                return false;
            }
        }
        return true;
    }

    private int prioridadModelo(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("flash") && (lower.contains("3.") || lower.contains("-3-"))) return 1;
        if (lower.contains("flash-lite")) return 2;
        if (lower.contains("flash")) return 3;
        if (lower.contains("pro")) return 4;
        return 5;
    }

    private String enviarConFallbackYReintentos(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> responseFormat,
            int maxTokens) {

        String clavePrincipal = limpiarClave(apiKey);
        if (!clavePrincipal.isBlank() && apiUrl != null && !apiUrl.isBlank()
                && !modelosDescubiertosPorHost.containsKey(extraerHost(apiUrl.trim()))) {
            descubrirModelosEnProveedor(apiUrl.trim(), clavePrincipal);
        }

        List<ProveedorConfig> proveedores = construirCadenaProveedores();
        if (proveedores.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de IA no está configurado (falta GEMINI_API_KEY o GROQ_API_KEY en .env).");
        }

        int reintentosMax = Math.max(0, maxReintentosPorProveedor);
        ResponseStatusException ultimoError = null;

        for (int pIdx = 0; pIdx < proveedores.size(); pIdx++) {
            ProveedorConfig proveedor = proveedores.get(pIdx);
            boolean haySiguienteProveedor = pIdx < proveedores.size() - 1;

            for (int intento = 0; intento <= reintentosMax; intento++) {
                try {
                    String resultado = ejecutarLlamadaUnica(proveedor, systemPrompt, userPrompt, responseFormat, maxTokens);
                    this.ultimoModeloEfectivo = proveedor.model();
                    return resultado;
                } catch (ModeloNoDisponibleException notFoundEx) {
                    ultimoError = new ResponseStatusException(HttpStatus.BAD_GATEWAY, notFoundEx.getMessage());
                    break;
                } catch (LimiteCuotaException rateEx) {
                    ultimoError = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Se alcanzó temporalmente el límite de solicitudes de la API de IA (" + extraerHost(proveedor.url()) + "). Intenta nuevamente en unos segundos.");
                    if (haySiguienteProveedor) {
                        break;
                    }
                    if (intento >= reintentosMax) {
                        throw ultimoError;
                    }
                    dormir(rateEx.esperaSugeridaMs);
                } catch (ResponseStatusException ex) {
                    ultimoError = ex;
                    int code = ex.getStatusCode().value();
                    boolean esRecuperable = (code == 429 || code == 502 || code == 503 || code == 504);
                    if (!esRecuperable) {
                        throw ex;
                    }
                    if (haySiguienteProveedor) {
                        break;
                    }
                    if (intento >= reintentosMax) {
                        throw ex;
                    }
                    dormir(2000L * (intento + 1));
                }
            }
        }

        throw ultimoError != null ? ultimoError : new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "No fue posible obtener respuesta de los proveedores de IA configurados.");
    }

    private String ejecutarLlamadaUnica(
            ProveedorConfig proveedor,
            String systemPrompt,
            String userPrompt,
            Map<String, Object> responseFormat,
            int maxTokens) {

        boolean esGoogle = proveedor.url().contains("googleapis.com");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", proveedor.model());
        requestBody.put("temperature", 0.1);
        if (esGoogle) {
            requestBody.put("max_tokens", maxTokens);
        } else {
            requestBody.put("max_completion_tokens", maxTokens);
        }
        requestBody.put("response_format", responseFormat);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        try {
            String rawResponse = restClient.post()
                    .uri(proveedor.url())
                    .header("Authorization", "Bearer " + proveedor.key())
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

        } catch (ModeloNoDisponibleException | LimiteCuotaException | ResponseStatusException e) {
            throw e;
        } catch (HttpClientErrorException.BadRequest e) {
            String detalle = extraerMensajeErrorProveedor(e.getResponseBodyAsString());
            System.err.println("[IA WARN] HTTP 400 en " + extraerHost(proveedor.url())
                    + " (modelo: " + proveedor.model() + ") -> " + detalle);
            throw new ModeloNoDisponibleException(
                    "El modelo '" + proveedor.model() + "' no soportó el formato estructurado en " + extraerHost(proveedor.url()) + ": " + detalle
            );
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            String detalle = extraerMensajeErrorProveedor(e.getResponseBodyAsString());
            System.err.println("[IA ERROR] HTTP " + e.getStatusCode().value() + " en " + extraerHost(proveedor.url())
                    + " (modelo: " + proveedor.model() + ") -> " + detalle);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Credenciales o permisos rechazados por " + extraerHost(proveedor.url()) + " (HTTP " + e.getStatusCode().value() + "): " + detalle);
        } catch (HttpClientErrorException.NotFound e) {
            String detalle = extraerMensajeErrorProveedor(e.getResponseBodyAsString());
            System.err.println("[IA WARN] Modelo no disponible (HTTP 404) en " + extraerHost(proveedor.url())
                    + " (modelo: " + proveedor.model() + ") -> " + detalle);
            throw new ModeloNoDisponibleException(
                    "El modelo de IA configurado ('" + proveedor.model() + "') no está disponible en " + extraerHost(proveedor.url()) + "."
            );
        } catch (HttpClientErrorException.TooManyRequests e) {
            long esperaMs = calcularEsperaRetryAfter(e.getResponseHeaders());
            throw new LimiteCuotaException(esperaMs);
        } catch (HttpServerErrorException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El proveedor externo de IA (" + extraerHost(proveedor.url()) + ") presenta indisponibilidad temporal (HTTP " + e.getStatusCode().value() + ").");
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Tiempo de espera agotado al comunicarse con " + extraerHost(proveedor.url()) + ".");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error inesperado de comunicación con el servicio externo de IA.");
        }
    }

    private String limpiarClave(String raw) {
        if (raw == null) return "";
        String limpia = raw.trim();
        if ((limpia.startsWith("\"") && limpia.endsWith("\""))
                || (limpia.startsWith("'") && limpia.endsWith("'"))) {
            limpia = limpia.substring(1, limpia.length() - 1).trim();
        }
        if (limpia.regionMatches(true, 0, "Bearer ", 0, 7)) {
            limpia = limpia.substring(7).trim();
        }
        return limpia;
    }

    private boolean mismoHost(String url1, String url2) {
        if (url1 == null || url2 == null || url1.isBlank() || url2.isBlank()) {
            return false;
        }
        return extraerHost(url1).equalsIgnoreCase(extraerHost(url2));
    }

    private String extraerHost(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    private String extraerMensajeErrorProveedor(String body) {
        if (body == null || body.isBlank()) {
            return "Sin detalle adicional del proveedor.";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errorNode = root.isArray() && !root.isEmpty() ? root.get(0).path("error") : root.path("error");
            String msg = errorNode.path("message").asText("");
            if (!msg.isBlank()) {
                return msg.length() > 220 ? msg.substring(0, 220) + "..." : msg;
            }
        } catch (Exception ignored) {
        }
        String plano = body.replaceAll("\\s+", " ").trim();
        return plano.length() > 220 ? plano.substring(0, 220) + "..." : plano;
    }

    private long calcularEsperaRetryAfter(HttpHeaders headers) {
        if (headers != null) {
            String retryAfter = headers.getFirst("Retry-After");
            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long segundos = (long) Math.ceil(Double.parseDouble(retryAfter.trim()));
                    if (segundos > 0) {
                        return Math.min(segundos * 1000L, MAX_ESPERA_RETRY_AFTER_MS);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 4000L;
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ModeloNoDisponibleException extends RuntimeException {
        ModeloNoDisponibleException(String message) {
            super(message);
        }
    }

    private static class LimiteCuotaException extends RuntimeException {
        final long esperaSugeridaMs;

        LimiteCuotaException(long esperaSugeridaMs) {
            this.esperaSugeridaMs = esperaSugeridaMs;
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

        Map<String, Object> reqProps = new LinkedHashMap<>();
        reqProps.put("requisitoId", Map.of("type", "integer"));
        reqProps.put("requisito", Map.of("type", "string"));
        reqProps.put("obligatorio", Map.of("type", "boolean"));
        reqProps.put("resultadoAnalisis", Map.of("type", "string", "enum", List.of("ENCONTRADO", "REQUIERE_VERIFICACION", "NO_IDENTIFICADO_EN_CV")));
        reqProps.put("condicionesEvaluadas", Map.of("type", "array", "items", Map.of("type", "string")));
        reqProps.put("experienciasRelacionadasIndices", Map.of("type", "array", "items", Map.of("type", "integer")));
        reqProps.put("observacionEvidencia", Map.of("type", "string"));
        reqProps.put("evidencia", schemaEvidencia());

        props.put("matrizRequisitos", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of(
                                "requisitoId",
                                "requisito",
                                "obligatorio",
                                "resultadoAnalisis",
                                "condicionesEvaluadas",
                                "experienciasRelacionadasIndices",
                                "observacionEvidencia",
                                "evidencia"
                        ),
                        "properties", reqProps
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