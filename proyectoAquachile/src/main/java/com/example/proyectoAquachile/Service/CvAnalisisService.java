package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.*;
import com.example.proyectoAquachile.Dto.PreguntasApoyoDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import com.example.proyectoAquachile.Service.ia.*;
import com.example.proyectoAquachile.Service.ia.RespuestaIaCrudaDTO.RequisitoIaItemDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio orquestador del análisis documental asistido por IA para AquaChile.
 * Separa la comprensión semántica del LLM de la validación determinística en Java.
 */
@Service
public class CvAnalisisService {

    public static final int MAX_PAGINAS_ANALISIS = ExtractorPdfService.MAX_PAGINAS_DEFAULT;
    public static final int MAX_CARACTERES_TOTALES = ExtractorPdfService.MAX_CARACTERES_POR_BLOQUE;
    private static final int MAX_PREGUNTAS_APOYO = 6;

    private final ExtractorPdfService extractorPdf;
    private final AnonimizadorPiiService anonimizadorPii;
    private final GroqApiClientService groqClient;
    private final ConstructorPromptsIaService constructorPrompts;
    private final VerificadorEvidenciaService verificadorEvidencia;
    private final CalculadoraExperienciaService calculadoraExperiencia;
    private final ConstructorMatrizRequisitosService constructorMatriz;
    private final ObjectMapper objectMapper;

    public CvAnalisisService(
            ExtractorPdfService extractorPdf,
            AnonimizadorPiiService anonimizadorPii,
            GroqApiClientService groqClient,
            ConstructorPromptsIaService constructorPrompts,
            VerificadorEvidenciaService verificadorEvidencia,
            CalculadoraExperienciaService calculadoraExperiencia,
            ConstructorMatrizRequisitosService constructorMatriz,
            ObjectMapper objectMapper) {
        this.extractorPdf = extractorPdf;
        this.anonimizadorPii = anonimizadorPii;
        this.groqClient = groqClient;
        this.constructorPrompts = constructorPrompts;
        this.verificadorEvidencia = verificadorEvidencia;
        this.calculadoraExperiencia = calculadoraExperiencia;
        this.constructorMatriz = constructorMatriz;
        this.objectMapper = objectMapper;
    }

    public String getModeloConfigurado() {
        return groqClient.getModeloConfigurado();
    }

    public Map<Integer, String> extraerTextoPorPagina(byte[] pdfBytes) {
        return extractorPdf.extraerDocumento(pdfBytes).paginasTexto();
    }

    public AnalisisCvDTO analizarPdf(
            byte[] pdfBytes,
            String nombreCandidatoInterno,
            String cargoPostulado,
            String familiaCargo,
            List<RequisitoCargo> requisitosCargo) {

        groqClient.validarConfiguracion();

        ExtractorPdfService.DocumentoExtraido docExtraido = extractorPdf.extraerDocumento(pdfBytes);
        Map<Integer, String> paginasOriginales = docExtraido.paginasTexto();
        Map<Integer, String> paginasAnonimizadas = new LinkedHashMap<>();

        for (Map.Entry<Integer, String> entry : paginasOriginales.entrySet()) {
            paginasAnonimizadas.put(
                    entry.getKey(),
                    anonimizadorPii.anonimizarTextoCv(entry.getValue(), nombreCandidatoInterno)
            );
        }

        var bloques = extractorPdf.dividirEnBloques(paginasAnonimizadas);
        boolean procesadoPorBloques = bloques.size() > 1;
        boolean documentoIncompleto = docExtraido.truncado();

        List<RespuestaIaCrudaDTO> resultadosParciales = new ArrayList<>();
        for (int i = 0; i < bloques.size(); i++) {
            Map<Integer, String> bloqueActual = bloques.get(i);
            ConstructorPromptsIaService.PromptPair prompt = constructorPrompts.construirPromptAnalisisCv(
                    cargoPostulado,
                    familiaCargo,
                    requisitosCargo,
                    bloqueActual,
                    i + 1,
                    bloques.size()
            );
            String jsonRespuesta = groqClient.ejecutarAnalisisEstructurado(
                    prompt.systemPrompt(),
                    prompt.userPrompt()
            );
            resultadosParciales.add(parsearAnalisis(jsonRespuesta));
        }

        RespuestaIaCrudaDTO combinado = combinarResultadosParciales(resultadosParciales);

        return consolidarYValidarDeterministico(
                combinado,
                nombreCandidatoInterno,
                requisitosCargo,
                paginasAnonimizadas,
                procesadoPorBloques,
                documentoIncompleto,
                docExtraido.paginasSinTexto()
        );
    }

    public PreguntasApoyoDTO generarPreguntasApoyo(
            AnalisisCvDTO datosValidados,
            String cargoPostula,
            String observacionesAnalista) {

        if (datosValidados == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No existen antecedentes validados para generar preguntas de apoyo.");
        }

        groqClient.validarConfiguracion();

        AnalisisCvDTO datosAnonimizados = anonimizadorPii.anonimizarAnalisisDto(datosValidados);
        String observacionesLimpias = anonimizadorPii.anonimizarObservaciones(observacionesAnalista);

        String resumenSerializado;
        try {
            resumenSerializado = objectMapper.writeValueAsString(datosAnonimizados);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo serializar el análisis validado.");
        }

        ConstructorPromptsIaService.PromptPair prompt = constructorPrompts.construirPromptPreguntasApoyo(
                cargoPostula,
                observacionesLimpias,
                resumenSerializado
        );

        String jsonRespuesta = groqClient.ejecutarPreguntasEstructuradas(
                prompt.systemPrompt(),
                prompt.userPrompt()
        );

        try {
            JsonNode root = objectMapper.readTree(jsonRespuesta);
            JsonNode arr = root.path("preguntas");
            if (!arr.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "La respuesta del proveedor de IA no contiene un arreglo válido de preguntas.");
            }

            List<PreguntasApoyoDTO.PreguntaSugeridaItem> preguntasLimpias = new ArrayList<>();
            Set<String> huellasDeduplicacion = new HashSet<>();

            for (JsonNode item : arr) {
                if (preguntasLimpias.size() >= MAX_PREGUNTAS_APOYO) {
                    break;
                }
                String pregunta = anonimizadorPii.anonimizarTextoLibre(item.path("pregunta").asText("")).trim();
                if (pregunta.isBlank()) {
                    continue;
                }
                String huella = pregunta.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (huella.length() > 8 && !huellasDeduplicacion.add(huella)) {
                    continue;
                }
                String objetivo = anonimizadorPii.anonimizarTextoLibre(
                        item.path("objetivoVerificacion").asText("Aclarar antecedentes declarados")
                ).trim();
                String relacionado = anonimizadorPii.anonimizarTextoLibre(
                        item.path("antecedenteRelacionado").asText("Antecedente del perfil")
                ).trim();

                preguntasLimpias.add(new PreguntasApoyoDTO.PreguntaSugeridaItem(
                        pregunta,
                        objetivo.isBlank() ? "Aclarar antecedentes declarados" : objetivo,
                        relacionado.isBlank() ? "Antecedente del perfil" : relacionado
                ));
            }

            return new PreguntasApoyoDTO(LocalDateTime.now(), groqClient.getModeloConfigurado(), preguntasLimpias);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Respuesta JSON inválida al procesar preguntas de apoyo.");
        }
    }

    private RespuestaIaCrudaDTO parsearAnalisis(String jsonTexto) {
        try {
            return objectMapper.readValue(jsonTexto, RespuestaIaCrudaDTO.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "La respuesta del proveedor de IA no cumplió el esquema estructurado esperado.");
        }
    }

    private RespuestaIaCrudaDTO combinarResultadosParciales(List<RespuestaIaCrudaDTO> parciales) {
        if (parciales.size() == 1) {
            return parciales.get(0);
        }
        String titulo = "NO_IDENTIFICADO_EN_CV";
        String resumen = "NO_IDENTIFICADO_EN_CV";
        List<FormacionItemDTO> formacion = new ArrayList<>();
        List<ExperienciaItemDTO> experiencias = new ArrayList<>();
        List<HabilidadItemDTO> habilidades = new ArrayList<>();
        List<CompetenciaItemDTO> competencias = new ArrayList<>();
        List<CertificacionItemDTO> certificaciones = new ArrayList<>();
        List<DatoFaltanteItemDTO> faltantes = new ArrayList<>();

        Map<Long, RequisitoIaItemDTO> matrizConsolidada = new LinkedHashMap<>();
        List<RequisitoIaItemDTO> matrizConDuplicadosIntraBloque = new ArrayList<>();

        for (RespuestaIaCrudaDTO p : parciales) {
            if ("NO_IDENTIFICADO_EN_CV".equals(titulo) && p.tituloProfesional() != null && !p.tituloProfesional().isBlank()) {
                titulo = p.tituloProfesional();
            }
            if ("NO_IDENTIFICADO_EN_CV".equals(resumen) && p.resumenProfesionalObjetivo() != null && !p.resumenProfesionalObjetivo().isBlank()) {
                resumen = p.resumenProfesionalObjetivo();
            }
            if (p.formacionAcademica() != null) formacion.addAll(p.formacionAcademica());

            int offsetExperiencia = experiencias.size();
            int totalExpBloque = p.experienciasLaborales() != null ? p.experienciasLaborales().size() : 0;
            if (p.experienciasLaborales() != null) {
                experiencias.addAll(p.experienciasLaborales());
            }

            if (p.habilidadesTecnicas() != null) habilidades.addAll(p.habilidadesTecnicas());
            if (p.competenciasDeclaradas() != null) competencias.addAll(p.competenciasDeclaradas());
            if (p.certificacionesYLicencias() != null) certificaciones.addAll(p.certificacionesYLicencias());
            if (p.datosFaltantesOAmbiguos() != null) faltantes.addAll(p.datosFaltantesOAmbiguos());

            if (p.matrizRequisitos() != null) {
                Set<Long> idsVistosEnBloque = new HashSet<>();
                for (RequisitoIaItemDTO item : p.matrizRequisitos()) {
                    if (item == null || item.requisitoId() == null) continue;
                    RequisitoIaItemDTO ajustado = desplazarIndicesExperiencia(item, offsetExperiencia, totalExpBloque);

                    // Si el LLM duplicó el mismo ID dentro del mismo bloque, conservar ambos para que la matriz lo marque
                    if (!idsVistosEnBloque.add(item.requisitoId())) {
                        matrizConDuplicadosIntraBloque.add(ajustado);
                        continue;
                    }

                    matrizConsolidada.merge(item.requisitoId(), ajustado, this::fusionarEvaluacionRequisito);
                }
            }
        }

        List<RequisitoIaItemDTO> matrizFinal = new ArrayList<>(matrizConsolidada.values());
        matrizFinal.addAll(matrizConDuplicadosIntraBloque);

        return new RespuestaIaCrudaDTO(
                "NO_IDENTIFICADO_EN_CV",
                titulo,
                resumen,
                formacion,
                experiencias,
                habilidades,
                competencias,
                certificaciones,
                matrizFinal,
                faltantes
        );
    }

    private RequisitoIaItemDTO desplazarIndicesExperiencia(
            RequisitoIaItemDTO item,
            int offset,
            int totalExpBloque) {
        if (item.experienciasRelacionadasIndices() == null || item.experienciasRelacionadasIndices().isEmpty()) {
            return item;
        }
        List<Integer> ajustados = new ArrayList<>();
        for (Integer idx : item.experienciasRelacionadasIndices()) {
            if (idx != null && idx >= 0 && idx < totalExpBloque) {
                ajustados.add(idx + offset);
            }
        }
        return new RequisitoIaItemDTO(
                item.requisitoId(),
                item.requisito(),
                item.obligatorio(),
                item.resultadoAnalisis(),
                item.condicionesEvaluadas(),
                ajustados,
                item.observacionEvidencia(),
                item.evidencia()
        );
    }

    private RequisitoIaItemDTO fusionarEvaluacionRequisito(RequisitoIaItemDTO a, RequisitoIaItemDTO b) {
        int rankA = prioridadEstado(a.resultadoAnalisis());
        int rankB = prioridadEstado(b.resultadoAnalisis());

        RequisitoIaItemDTO principal = rankB > rankA ? b : a;
        RequisitoIaItemDTO secundario = rankB > rankA ? a : b;

        if (rankA == rankB
                && (principal.evidencia() == null || principal.evidencia().fragmento() == null || principal.evidencia().fragmento().isBlank())
                && secundario.evidencia() != null && secundario.evidencia().fragmento() != null && !secundario.evidencia().fragmento().isBlank()) {
            principal = secundario;
            secundario = (principal == a) ? b : a;
        }

        Set<String> condiciones = new LinkedHashSet<>();
        if (a.condicionesEvaluadas() != null) condiciones.addAll(a.condicionesEvaluadas());
        if (b.condicionesEvaluadas() != null) condiciones.addAll(b.condicionesEvaluadas());

        Set<Integer> indices = new TreeSet<>();
        if (a.experienciasRelacionadasIndices() != null) indices.addAll(a.experienciasRelacionadasIndices());
        if (b.experienciasRelacionadasIndices() != null) indices.addAll(b.experienciasRelacionadasIndices());

        return new RequisitoIaItemDTO(
                principal.requisitoId(),
                principal.requisito(),
                principal.obligatorio(),
                principal.resultadoAnalisis(),
                new ArrayList<>(condiciones),
                new ArrayList<>(indices),
                principal.observacionEvidencia(),
                principal.evidencia()
        );
    }

    private int prioridadEstado(String estado) {
        if (estado == null) return 0;
        return switch (estado.trim().toUpperCase(Locale.ROOT)) {
            case "ENCONTRADO" -> 3;
            case "REQUIERE_VERIFICACION" -> 2;
            case "NO_IDENTIFICADO_EN_CV" -> 1;
            default -> 0;
        };
    }

    private AnalisisCvDTO consolidarYValidarDeterministico(
            RespuestaIaCrudaDTO crudo,
            String nombreCandidatoInterno,
            List<RequisitoCargo> requisitosReales,
            Map<Integer, String> paginasAnonimizadas,
            boolean procesadoPorBloques,
            boolean documentoIncompleto,
            List<Integer> paginasSinTexto) {

        List<FormacionItemDTO> formacion = crudo.formacionAcademica() == null ? List.of() :
                crudo.formacionAcademica().stream().map(f -> {
                    EvidenciaDTO ev = verificadorEvidencia.verificarEvidenciaContextual(
                            f.evidencia(), "FORMACION", f.titulo(), paginasAnonimizadas);
                    return new FormacionItemDTO(
                            valorONoIdentificado(f.titulo()),
                            valorONoIdentificado(f.institucion()),
                            valorONoIdentificado(f.periodo()),
                            valorONoIdentificado(f.estadoAcademico()),
                            ev,
                            "PENDIENTE"
                    );
                }).toList();

        List<ExperienciaItemDTO> experiencias = crudo.experienciasLaborales() == null ? List.of() :
                crudo.experienciasLaborales().stream().map(e -> {
                    EvidenciaDTO ev = verificadorEvidencia.verificarEvidenciaContextual(
                            e.evidencia(), "EXPERIENCIA", e.cargo(), paginasAnonimizadas);
                    String periodoLimpio = valorONoIdentificado(e.periodo());
                    String duracionDeterministica = calculadoraExperiencia.calcularDuracionIndividual(
                            periodoLimpio,
                            e.duracionCalculada()
                    );
                    return new ExperienciaItemDTO(
                            valorONoIdentificado(e.cargo()),
                            valorONoIdentificado(e.empresa()),
                            periodoLimpio,
                            duracionDeterministica,
                            e.funciones() == null ? List.of() : e.funciones(),
                            ev,
                            "PENDIENTE"
                    );
                }).toList();

        List<HabilidadItemDTO> habilidades = crudo.habilidadesTecnicas() == null ? List.of() :
                crudo.habilidadesTecnicas().stream().map(h -> new HabilidadItemDTO(
                        valorONoIdentificado(h.nombre()),
                        valorONoIdentificado(h.contextoUso()),
                        verificadorEvidencia.verificarEvidenciaContextual(
                                h.evidencia(), "HABILIDAD_TECNICA", h.nombre(), paginasAnonimizadas),
                        "PENDIENTE"
                )).toList();

        List<CompetenciaItemDTO> competencias = crudo.competenciasDeclaradas() == null ? List.of() :
                crudo.competenciasDeclaradas().stream().map(c -> new CompetenciaItemDTO(
                        valorONoIdentificado(c.nombre()),
                        verificadorEvidencia.verificarEvidenciaContextual(
                                c.evidencia(), "COMPETENCIA", c.nombre(), paginasAnonimizadas),
                        "PENDIENTE"
                )).toList();

        List<CertificacionItemDTO> certificaciones = crudo.certificacionesYLicencias() == null ? List.of() :
                crudo.certificacionesYLicencias().stream().map(c -> new CertificacionItemDTO(
                        valorONoIdentificado(c.nombre()),
                        valorONoIdentificado(c.entidadEmisora()),
                        valorONoIdentificado(c.vigencia()),
                        verificadorEvidencia.verificarEvidenciaContextual(
                                c.evidencia(), "CERTIFICACION", c.nombre(), paginasAnonimizadas),
                        "PENDIENTE"
                )).toList();

        List<RequisitoMatrizItemDTO> matrizVerificada = constructorMatriz.construirMatriz(
                requisitosReales,
                crudo.matrizRequisitos(),
                experiencias,
                paginasAnonimizadas,
                documentoIncompleto
        );

        List<DatoFaltanteItemDTO> faltantes = new ArrayList<>();
        if (crudo.datosFaltantesOAmbiguos() != null) {
            for (DatoFaltanteItemDTO d : crudo.datosFaltantesOAmbiguos()) {
                faltantes.add(new DatoFaltanteItemDTO(
                        valorONoIdentificado(d.antecedente()),
                        valorONoIdentificado(d.motivo()),
                        true
                ));
            }
        }

        CalculadoraExperienciaService.ResumenCronologicoGlobal resumenExp =
                calculadoraExperiencia.calcularExperienciaCronologicaGlobal(experiencias);
        if (resumenExp.tieneSolapamientos()) {
            faltantes.add(new DatoFaltanteItemDTO(
                    "Periodos laborales simultáneos o superpuestos",
                    resumenExp.notaSolapamiento(),
                    true
            ));
        }
        if (documentoIncompleto) {
            faltantes.add(new DatoFaltanteItemDTO(
                    "Documento truncado por límite de seguridad",
                    "El currículum superó la capacidad máxima procesable; algunas secciones finales no pudieron evaluarse completamente.",
                    true
            ));
        } else if (procesadoPorBloques) {
            faltantes.add(new DatoFaltanteItemDTO(
                    "Documento extenso procesado por bloques de páginas",
                    "El currículum fue analizado en múltiples bloques de páginas conservando la numeración original.",
                    true
            ));
        }
        if (paginasSinTexto != null && !paginasSinTexto.isEmpty()) {
            faltantes.add(new DatoFaltanteItemDTO(
                    "Páginas sin texto seleccionable: " + paginasSinTexto,
                    "Algunas páginas del PDF contienen únicamente imágenes o contenido escaneado sin capa de texto (requiere revisión manual).",
                    true
            ));
        }

        return new AnalisisCvDTO(
                nombreCandidatoInterno,
                valorONoIdentificado(crudo.tituloProfesional()),
                valorONoIdentificado(crudo.resumenProfesionalObjetivo()),
                formacion,
                experiencias,
                habilidades,
                competencias,
                certificaciones,
                matrizVerificada,
                faltantes
        );
    }

    private String valorONoIdentificado(String valor) {
        return (valor == null || valor.isBlank()) ? "NO_IDENTIFICADO_EN_CV" : valor.trim();
    }
}