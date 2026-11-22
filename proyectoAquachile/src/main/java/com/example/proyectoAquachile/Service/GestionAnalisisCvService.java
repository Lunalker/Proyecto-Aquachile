package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvResponseDTO;
import com.example.proyectoAquachile.Dto.PreguntasApoyoDTO;
import com.example.proyectoAquachile.Dto.ValidacionAnalisisRequestDTO;
import com.example.proyectoAquachile.Model.*;
import com.example.proyectoAquachile.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GestionAnalisisCvService {

    private static final Logger log = LoggerFactory.getLogger(GestionAnalisisCvService.class);
    private static final String VERIFICADA_TEXTUAL = "VERIFICADA_TEXTUAL";
    private static final String REVISION_HUMANA = "REVISION_HUMANA";
    private static final String MODIFICADO_ANALISTA = "MODIFICADO_ANALISTA";

    private final DocumentoCandidatoRepository documentos;
    private final AnalisisCvRepository analisisRepo;
    private final RequisitoCargoRepository requisitosRepo;
    private final SolicitudEvaluacionRepository solicitudesRepo;
    private final CvAnalisisService cvAnalisisService;
    private final ObjectMapper objectMapper;
    private final PostulacionRepository postulacionesRepo;
    private final DocumentoPostulacionRepository documentosPostulacionRepo;

    @Autowired
    public GestionAnalisisCvService(
            DocumentoCandidatoRepository documentos,
            AnalisisCvRepository analisisRepo,
            RequisitoCargoRepository requisitosRepo,
            SolicitudEvaluacionRepository solicitudesRepo,
            CvAnalisisService cvAnalisisService,
            ObjectMapper objectMapper,
            PostulacionRepository postulacionesRepo,
            DocumentoPostulacionRepository documentosPostulacionRepo) {
        this.documentos = Objects.requireNonNull(documentos, "DocumentoCandidatoRepository es obligatorio");
        this.analisisRepo = Objects.requireNonNull(analisisRepo, "AnalisisCvRepository es obligatorio");
        this.requisitosRepo = Objects.requireNonNull(requisitosRepo, "RequisitoCargoRepository es obligatorio");
        this.solicitudesRepo = Objects.requireNonNull(solicitudesRepo, "SolicitudEvaluacionRepository es obligatorio");
        this.cvAnalisisService = Objects.requireNonNull(cvAnalisisService, "CvAnalisisService es obligatorio");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper es obligatorio");
        this.postulacionesRepo = Objects.requireNonNull(postulacionesRepo, "PostulacionRepository es obligatorio");
        this.documentosPostulacionRepo = Objects.requireNonNull(documentosPostulacionRepo, "DocumentoPostulacionRepository es obligatorio");
    }

    // Constructor delegado únicamente para compatibilidad con pruebas unitarias heredadas
    public GestionAnalisisCvService(
            DocumentoCandidatoRepository documentos,
            AnalisisCvRepository analisisRepo,
            RequisitoCargoRepository requisitosRepo,
            SolicitudEvaluacionRepository solicitudesRepo,
            CvAnalisisService cvAnalisisService,
            ObjectMapper objectMapper) {
        this.documentos = documentos;
        this.analisisRepo = analisisRepo;
        this.requisitosRepo = requisitosRepo;
        this.solicitudesRepo = solicitudesRepo;
        this.cvAnalisisService = cvAnalisisService;
        this.objectMapper = objectMapper;
        this.postulacionesRepo = null;
        this.documentosPostulacionRepo = null;
    }

    // =========================================================================
    // FLUJO PRINCIPAL VINCULADO A POSTULACIÓN (postulacionId)
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisActivoPorPostulacion(Long postulacionId, boolean soloValidado) {
        verificarRepositoriosPostulacion();
        Postulacion postulacion = postulacionesRepo.findById(postulacionId).orElse(null);
        DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(postulacionId).orElse(null);
        if (postulacion == null || doc == null || postulacion.getCandidato() == null) {
            return Optional.empty();
        }
        if (doc.getPostulacion() == null || !postulacionId.equals(doc.getPostulacion().getId())) {
            return Optional.empty();
        }

        String hashActual = doc.getHashSha256();
        Long candidatoId = postulacion.getCandidato().getId();

        return analisisRepo.findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        postulacionId, AnalisisCv.ANALISIS_GENERADO)
                .filter(a -> postulacionId.equals(a.getPostulacionId()))
                .filter(a -> candidatoId.equals(a.getCandidatoId()))
                .filter(a -> a.getDocumentoPostulacion() == null || doc.getId().equals(a.getDocumentoPostulacion().getId()))
                .filter(a -> hashActual != null && hashActual.equals(a.getHashDocumento()))
                .filter(a -> !soloValidado || AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                .map(a -> mapearAResponsePostulacion(a, postulacion));
    }

    public AnalisisCvResponseDTO generarORecuperarAnalisisPorPostulacion(Long postulacionId, boolean forzarReanalisis) {
        PostulacionSnapshot snapshot = leerSnapshotPostulacion(postulacionId);

        if (!"application/pdf".equalsIgnoreCase(snapshot.tipoContenido())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El análisis asistido por IA solo está disponible para documentos en formato PDF auténtico.");
        }

        if (!forzarReanalisis) {
            Optional<AnalisisCvResponseDTO> existente = obtenerAnalisisActivoPorPostulacion(postulacionId, false);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        List<RequisitoCargo> requisitos = (snapshot.cargoOferta() == null || snapshot.cargoOferta().isBlank())
                ? List.of()
                : requisitosRepo.findByCargoIgnoreCaseAndActivoTrueOrderByIdAsc(snapshot.cargoOferta());

        // Procesamiento externo con Groq fuera de cualquier transacción de base de datos
        AnalisisCvDTO resultadoIa = cvAnalisisService.analizarPdf(
                snapshot.contenido(),
                snapshot.nombreCandidato(),
                snapshot.cargoOferta(),
                snapshot.categoriaOferta(),
                requisitos
        );

        return persistirNuevoAnalisisPostulacion(snapshot, resultadoIa);
    }

    @Transactional
    public AnalisisCvResponseDTO guardarRevisionOValidarPorPostulacion(
            Long postulacionId,
            ValidacionAnalisisRequestDTO request,
            String usernameAnalista) {

        if (request == null || request.datosRevisados() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los datos revisados por el analista.");
        }
        verificarRepositoriosPostulacion();

        Postulacion postulacion = postulacionesRepo.findById(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación no tiene CV adjunto."));

        AnalisisCv analisis = analisisRepo.findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        postulacionId, AnalisisCv.ANALISIS_GENERADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un análisis activo para revisar en esta postulación."));

        // Comprobación estricta de pertenencia y vigencia del documento
        if (!AnalisisCv.ANALISIS_GENERADO.equals(analisis.getEstadoAnalisis())
                || !postulacionId.equals(analisis.getPostulacionId())
                || !postulacion.getCandidato().getId().equals(analisis.getCandidatoId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El análisis no corresponde a esta postulación o ya fue invalidado.");
        }
        if (analisis.getDocumentoPostulacion() != null
                && !doc.getId().equals(analisis.getDocumentoPostulacion().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento asociado a la postulación ha cambiado. Regenera el análisis antes de validar.");
        }
        if (doc.getHashSha256() == null || !doc.getHashSha256().equals(analisis.getHashDocumento())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El contenido del CV cambió después de ejecutarse el análisis. Regenera el análisis sobre el CV vigente.");
        }

        try {
            AnalisisCvDTO originalIa = objectMapper.readValue(analisis.getResultadoIaJson(), AnalisisCvDTO.class);
            AnalisisCvDTO sanitizado = sanitizarRevisionHumana(originalIa, request.datosRevisados());
            // resultadoIaJson nunca se sobrescribe; solo se actualiza datosValidadosJson
            analisis.setDatosValidadosJson(objectMapper.writeValueAsString(sanitizado));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido en los datos revisados.");
        }

        analisis.setObservacionesAnalista(request.observacionesAnalista());

        if (request.confirmarValidacion()) {
            analisis.setEstadoRevision(AnalisisCv.REVISION_VALIDADO);
            analisis.setUsuarioValidador(usernameAnalista);
            analisis.setFechaValidacion(LocalDateTime.now());
        } else {
            analisis.setEstadoRevision(AnalisisCv.REVISION_EN_CURSO);
        }

        AnalisisCv guardado = analisisRepo.save(analisis);
        return mapearAResponsePostulacion(guardado, postulacion);
    }

    @Transactional
    public void invalidarAnalisisPreviosPorPostulacion(Long postulacionId) {
        List<AnalisisCv> vigentes = analisisRepo.findByPostulacionIdAndEstadoAnalisis(
                postulacionId, AnalisisCv.ANALISIS_GENERADO);
        for (AnalisisCv item : vigentes) {
            item.setEstadoAnalisis(AnalisisCv.ANALISIS_INVALIDADO);
        }
        if (!vigentes.isEmpty()) {
            analisisRepo.saveAll(vigentes);
        }
    }

    @Transactional(readOnly = true)
    protected PostulacionSnapshot leerSnapshotPostulacion(Long postulacionId) {
        verificarRepositoriosPostulacion();
        Postulacion p = postulacionesRepo.findById(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación no tiene CV adjunto."));

        return new PostulacionSnapshot(
                p.getId(),
                doc.getId(),
                p.getCandidato().getId(),
                p.getOfertaLaboral().getId(),
                p.getCandidato().getNombre(),
                p.getOfertaLaboral().getCargo(),
                p.getOfertaLaboral().getCategoria(),
                doc.getNombreOriginal(),
                doc.getTipoMime(),
                doc.getContenido(),
                doc.getHashSha256()
        );
    }

    @Transactional
    protected AnalisisCvResponseDTO persistirNuevoAnalisisPostulacion(PostulacionSnapshot snapshot, AnalisisCvDTO resultadoIa) {
        verificarRepositoriosPostulacion();

        // 1. Reconsultar entidades vigentes antes de invalidar nada
        Postulacion postulacionRef = postulacionesRepo.findById(snapshot.postulacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación fue eliminada durante el análisis."));
        DocumentoPostulacion docRef = documentosPostulacionRepo.findById(snapshot.documentoPostulacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El documento fue eliminado durante el análisis."));

        // 2. Comprobar integridad referencial y hash SHA-256 contra condición de carrera
        boolean mismaPostulacion = docRef.getPostulacion() != null
                && snapshot.postulacionId().equals(docRef.getPostulacion().getId());
        boolean mismoCandidato = postulacionRef.getCandidato() != null
                && snapshot.candidatoId().equals(postulacionRef.getCandidato().getId());
        boolean mismaOferta = postulacionRef.getOfertaLaboral() != null
                && snapshot.ofertaId().equals(postulacionRef.getOfertaLaboral().getId());
        boolean mismoHash = docRef.getHashSha256() != null
                && snapshot.hashSha256().equals(docRef.getHashSha256());

        if (!mismaPostulacion || !mismoCandidato || !mismaOferta || !mismoHash) {
            log.warn("Condición de carrera detectada al persistir análisis de postulación {}: hash/asociación modificada.", snapshot.postulacionId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento CV de la postulación cambió mientras se procesaba el análisis. Vuelve a ejecutar el análisis sobre el archivo vigente.");
        }

        // 3. Solo tras confirmar consistencia, invalidar análisis previos de ESTA postulación y persistir
        invalidarAnalisisPreviosPorPostulacion(snapshot.postulacionId());

        String jsonSerializado;
        try {
            jsonSerializado = objectMapper.writeValueAsString(resultadoIa);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serializando el análisis de IA.");
        }

        AnalisisCv entidad = new AnalisisCv();
        entidad.setPostulacionId(snapshot.postulacionId());
        entidad.setDocumentoPostulacion(docRef);
        entidad.setCandidatoId(snapshot.candidatoId());
        entidad.setNombreArchivoAnalizado(snapshot.nombreArchivo());
        entidad.setHashDocumento(snapshot.hashSha256());
        entidad.setModeloUtilizado(cvAnalisisService.getModeloConfigurado());
        entidad.setFechaGeneracion(LocalDateTime.now());
        entidad.setResultadoIaJson(jsonSerializado);
        entidad.setDatosValidadosJson(jsonSerializado);
        entidad.setEstadoAnalisis(AnalisisCv.ANALISIS_GENERADO);
        entidad.setEstadoRevision(AnalisisCv.REVISION_PENDIENTE);

        AnalisisCv guardado = analisisRepo.save(entidad);
        return mapearAResponsePostulacion(guardado, postulacionRef);
    }

    // =========================================================================
    // RESOLUCIÓN INEQUÍVOCA PARA SOLICITUDES DE EVALUACIÓN
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisValidadoPorSolicitud(Long solicitudId) {
        SolicitudEvaluacion solicitud = solicitudesRepo.findById(solicitudId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de evaluación no encontrada"));

        return resolverAnalisisValidadoParaSolicitud(solicitud)
                .map(res -> res.postulacion() != null
                        ? mapearAResponsePostulacion(res.analisis(), res.postulacion())
                        : mapearAResponse(res.analisis(), solicitud.getCandidato()));
    }

    private Optional<AnalisisResuelto> resolverAnalisisValidadoParaSolicitud(SolicitudEvaluacion solicitud) {
        Long candidatoId = solicitud.getCandidato().getId();

        // 1. Vínculo referencial explícito solicitud -> postulación (fuente de verdad inequívoca)
        Long postulacionExplicitaId = solicitud.getPostulacionId();
        if (postulacionExplicitaId != null) {
            verificarRepositoriosPostulacion();
            Postulacion p = postulacionesRepo.findById(postulacionExplicitaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "La postulación vinculada a esta solicitud ya no existe."));
            if (!candidatoId.equals(p.getCandidato().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Inconsistencia referencial: la postulación vinculada no pertenece al candidato de la solicitud.");
            }
            DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(p.getId()).orElse(null);
            if (doc == null || doc.getHashSha256() == null) {
                return Optional.empty();
            }
            return analisisRepo.findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                            p.getId(), AnalisisCv.ANALISIS_GENERADO)
                    .filter(a -> p.getId().equals(a.getPostulacionId()))
                    .filter(a -> doc.getHashSha256().equals(a.getHashDocumento()))
                    .filter(a -> AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                    .map(a -> new AnalisisResuelto(a, p, p.getOfertaLaboral().getCargo()));
        }

        // 2. Compatibilidad estricta con solicitudes antiguas sin postulacion_id:
        // Nunca mezclar análisis de otro cargo ni elegir arbitrariamente ante ambigüedad.
        if (postulacionesRepo != null && documentosPostulacionRepo != null) {
            List<Postulacion> postulaciones = postulacionesRepo.findByCandidato_IdOrderByFechaPostulacionDesc(candidatoId);
            if (!postulaciones.isEmpty()) {
                String cargoSolicitud = solicitud.getCargo() != null ? solicitud.getCargo().trim() : "";
                if (cargoSolicitud.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Solicitud ambigua: el candidato tiene postulaciones registradas pero la solicitud no especifica cargo ni postulación.");
                }

                List<Postulacion> coincidentes = postulaciones.stream()
                        .filter(p -> p.getOfertaLaboral() != null
                                && cargoSolicitud.equalsIgnoreCase(p.getOfertaLaboral().getCargo().trim()))
                        .toList();

                if (coincidentes.size() > 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Solicitud ambigua: el candidato tiene múltiples postulaciones para el cargo '" + cargoSolicitud + "'. Vincula la solicitud a una postulación específica.");
                }

                if (coincidentes.size() == 1) {
                    Postulacion unica = coincidentes.get(0);
                    DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(unica.getId()).orElse(null);
                    if (doc == null || doc.getHashSha256() == null) {
                        return Optional.empty();
                    }
                    return analisisRepo.findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                                    unica.getId(), AnalisisCv.ANALISIS_GENERADO)
                            .filter(a -> unica.getId().equals(a.getPostulacionId()))
                            .filter(a -> doc.getHashSha256().equals(a.getHashDocumento()))
                            .filter(a -> AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                            .map(a -> new AnalisisResuelto(a, unica, unica.getOfertaLaboral().getCargo()));
                }

                // Si existen postulaciones pero ninguna corresponde al cargo solicitado, no contaminar con otro cargo.
                if (postulaciones.size() > 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Solicitud ambigua: el candidato posee múltiples postulaciones y ninguna coincide exactamente con el cargo '" + cargoSolicitud + "'.");
                }
            }
        }

        // 3. Flujo heredado por candidato (solo si postulacionId == null y el cargo es compatible)
        DocumentoCandidato docLegacy = documentos.findByCandidato_Id(candidatoId).orElse(null);
        if (docLegacy == null) {
            return Optional.empty();
        }
        String cargoSol = solicitud.getCargo();
        String cargoCand = solicitud.getCandidato().getCargoPostula();
        if (cargoSol != null && !cargoSol.isBlank() && cargoCand != null && !cargoCand.isBlank()
                && !cargoSol.trim().equalsIgnoreCase(cargoCand.trim())) {
            return Optional.empty();
        }

        String hashActual = AnonimizadorCvUtil.calcularSha256(docLegacy.getContenido());
        return analisisRepo.findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        candidatoId, AnalisisCv.ANALISIS_GENERADO)
                .filter(a -> a.getPostulacionId() == null)
                .filter(a -> hashActual.equals(a.getHashDocumento()))
                .filter(a -> AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                .map(a -> new AnalisisResuelto(a, null,
                        (cargoSol != null && !cargoSol.isBlank()) ? cargoSol : cargoCand));
    }

    // =========================================================================
    // FLUJO HEREDADO POR CANDIDATO (candidatoId)
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisActivo(Long candidatoId, boolean soloValidado) {
        DocumentoCandidato doc = documentos.findByCandidato_Id(candidatoId).orElse(null);
        if (doc == null) {
            return Optional.empty();
        }
        String hashActual = AnonimizadorCvUtil.calcularSha256(doc.getContenido());

        return analisisRepo.findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        candidatoId, AnalisisCv.ANALISIS_GENERADO)
                .filter(a -> a.getPostulacionId() == null)
                .filter(a -> candidatoId.equals(a.getCandidatoId()))
                .filter(a -> hashActual.equals(a.getHashDocumento()))
                .filter(a -> !soloValidado || AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                .map(a -> mapearAResponse(a, doc.getCandidato()));
    }

    public AnalisisCvResponseDTO generarORecuperarAnalisis(Long candidatoId, boolean forzarReanalisis) {
        DocumentoSnapshot snapshot = leerSnapshotDocumento(candidatoId);

        if (!"application/pdf".equalsIgnoreCase(snapshot.tipoContenido())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El análisis asistido por IA solo está disponible para documentos en formato PDF auténtico.");
        }

        if (!forzarReanalisis) {
            Optional<AnalisisCvResponseDTO> existente = obtenerAnalisisActivo(candidatoId, false);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        List<RequisitoCargo> requisitos = (snapshot.cargoPostula() == null || snapshot.cargoPostula().isBlank())
                ? List.of()
                : requisitosRepo.findByCargoIgnoreCaseAndActivoTrueOrderByIdAsc(snapshot.cargoPostula());

        AnalisisCvDTO resultadoIa = cvAnalisisService.analizarPdf(
                snapshot.contenido(),
                snapshot.nombreCandidato(),
                snapshot.cargoPostula(),
                snapshot.familiaCargo(),
                requisitos
        );

        return persistirNuevoAnalisis(snapshot, resultadoIa);
    }

    @Transactional
    public AnalisisCvResponseDTO guardarRevisionOValidar(
            Long candidatoId,
            ValidacionAnalisisRequestDTO request,
            String usernameAnalista) {

        if (request == null || request.datosRevisados() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los datos revisados por el analista.");
        }

        DocumentoCandidato doc = documentos.findByCandidato_Id(candidatoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay CV adjunto para este candidato."));

        AnalisisCv analisis = analisisRepo.findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        candidatoId, AnalisisCv.ANALISIS_GENERADO)
                .filter(a -> a.getPostulacionId() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un análisis activo para revisar. Genera primero el análisis del CV."));

        String hashActual = AnonimizadorCvUtil.calcularSha256(doc.getContenido());
        if (!AnalisisCv.ANALISIS_GENERADO.equals(analisis.getEstadoAnalisis())
                || !candidatoId.equals(analisis.getCandidatoId())
                || !hashActual.equals(analisis.getHashDocumento())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento CV del candidato cambió o el análisis ya no está vigente. Regenera el análisis antes de validar.");
        }

        try {
            AnalisisCvDTO originalIa = objectMapper.readValue(analisis.getResultadoIaJson(), AnalisisCvDTO.class);
            AnalisisCvDTO sanitizado = sanitizarRevisionHumana(originalIa, request.datosRevisados());
            analisis.setDatosValidadosJson(objectMapper.writeValueAsString(sanitizado));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido en los datos revisados.");
        }

        analisis.setObservacionesAnalista(request.observacionesAnalista());

        if (request.confirmarValidacion()) {
            analisis.setEstadoRevision(AnalisisCv.REVISION_VALIDADO);
            analisis.setUsuarioValidador(usernameAnalista);
            analisis.setFechaValidacion(LocalDateTime.now());
        } else {
            analisis.setEstadoRevision(AnalisisCv.REVISION_EN_CURSO);
        }

        AnalisisCv guardado = analisisRepo.save(analisis);
        return mapearAResponse(guardado, doc.getCandidato());
    }

    public PreguntasApoyoDTO generarPreguntasApoyoParaSolicitud(Long solicitudId) {
        SolicitudContexto ctx = leerContextoSolicitudValidado(solicitudId);

        PreguntasApoyoDTO preguntas = cvAnalisisService.generarPreguntasApoyo(
                ctx.datosValidados(),
                ctx.cargo(),
                ctx.observacionesAnalista()
        );

        guardarPreguntasEnAnalisis(ctx.analisisId(), preguntas);
        return preguntas;
    }

    @Transactional
    public void invalidarAnalisisPrevios(Long candidatoId) {
        List<AnalisisCv> vigentes = analisisRepo.findByCandidatoIdAndEstadoAnalisis(
                candidatoId, AnalisisCv.ANALISIS_GENERADO);
        List<AnalisisCv> soloLegacy = vigentes.stream()
                .filter(item -> item.getPostulacionId() == null)
                .peek(item -> item.setEstadoAnalisis(AnalisisCv.ANALISIS_INVALIDADO))
                .toList();
        if (!soloLegacy.isEmpty()) {
            analisisRepo.saveAll(soloLegacy);
        }
    }

    @Transactional(readOnly = true)
    protected DocumentoSnapshot leerSnapshotDocumento(Long candidatoId) {
        DocumentoCandidato doc = documentos.findByCandidato_Id(candidatoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay CV adjunto a este candidato"));
        Candidato c = doc.getCandidato();
        return new DocumentoSnapshot(
                doc.getId(),
                c.getId(),
                c.getNombre(),
                c.getCargoPostula(),
                c.getFamiliaCargo(),
                doc.getNombreArchivo(),
                doc.getTipoContenido(),
                doc.getContenido(),
                AnonimizadorCvUtil.calcularSha256(doc.getContenido())
        );
    }

    @Transactional
    protected AnalisisCvResponseDTO persistirNuevoAnalisis(DocumentoSnapshot snapshot, AnalisisCvDTO resultadoIa) {
        DocumentoCandidato docRef = documentos.findById(snapshot.documentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El documento fue eliminado durante el análisis."));

        boolean mismoCandidato = docRef.getCandidato() != null
                && snapshot.candidatoId().equals(docRef.getCandidato().getId());
        String hashActual = AnonimizadorCvUtil.calcularSha256(docRef.getContenido());
        boolean mismoHash = snapshot.hashSha256().equals(hashActual);

        if (!mismoCandidato || !mismoHash) {
            log.warn("Condición de carrera detectada al persistir análisis legado del candidato {}: hash/propietario modificado.", snapshot.candidatoId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento CV del candidato fue modificado mientras se procesaba el análisis. Vuelve a ejecutarlo sobre el archivo vigente.");
        }

        invalidarAnalisisPrevios(snapshot.candidatoId());

        String jsonSerializado;
        try {
            jsonSerializado = objectMapper.writeValueAsString(resultadoIa);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serializando el análisis de IA.");
        }

        AnalisisCv entidad = new AnalisisCv();
        entidad.setDocumento(docRef);
        entidad.setCandidatoId(snapshot.candidatoId());
        entidad.setNombreArchivoAnalizado(snapshot.nombreArchivo());
        entidad.setHashDocumento(snapshot.hashSha256());
        entidad.setModeloUtilizado(cvAnalisisService.getModeloConfigurado());
        entidad.setFechaGeneracion(LocalDateTime.now());
        entidad.setResultadoIaJson(jsonSerializado);
        entidad.setDatosValidadosJson(jsonSerializado);
        entidad.setEstadoAnalisis(AnalisisCv.ANALISIS_GENERADO);
        entidad.setEstadoRevision(AnalisisCv.REVISION_PENDIENTE);

        docRef.setAnalisisIa(jsonSerializado);
        documentos.save(docRef);
        AnalisisCv guardado = analisisRepo.save(entidad);

        return mapearAResponse(guardado, docRef.getCandidato());
    }

    @Transactional(readOnly = true)
    protected SolicitudContexto leerContextoSolicitudValidado(Long solicitudId) {
        SolicitudEvaluacion sol = solicitudesRepo.findById(solicitudId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada."));

        AnalisisResuelto resuelto = resolverAnalisisValidadoParaSolicitud(sol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La postulación o candidato de esta solicitud aún no cuenta con un análisis de CV validado sobre el documento vigente."));

        AnalisisCv analisis = resuelto.analisis();
        if (!AnalisisCv.REVISION_VALIDADO.equals(analisis.getEstadoRevision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El análisis del CV aún está pendiente de validación por parte del analista.");
        }

        try {
            String jsonFuente = analisis.getDatosValidadosJson() != null
                    ? analisis.getDatosValidadosJson()
                    : analisis.getResultadoIaJson();
            AnalisisCvDTO datos = objectMapper.readValue(jsonFuente, AnalisisCvDTO.class);
            return new SolicitudContexto(analisis.getId(), resuelto.cargoEfectivo(), analisis.getObservacionesAnalista(), datos);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error leyendo los antecedentes validados.");
        }
    }

    @Transactional
    protected void guardarPreguntasEnAnalisis(Long analisisId, PreguntasApoyoDTO preguntas) {
        AnalisisCv analisis = analisisRepo.findById(analisisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el análisis asociado para almacenar las preguntas de apoyo."));
        try {
            analisis.setPreguntasApoyoJson(objectMapper.writeValueAsString(preguntas));
            analisisRepo.save(analisis);
        } catch (Exception e) {
            log.error("Error persistiendo preguntas de apoyo en analisisId={}: {}", analisisId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudieron guardar las preguntas de apoyo generadas.");
        }
    }

    // =========================================================================
    // SANITIZACIÓN DE REVISIÓN HUMANA (SEPARACIÓN IA VS. EDICIÓN MANUAL)
    // =========================================================================

    private AnalisisCvDTO sanitizarRevisionHumana(AnalisisCvDTO original, AnalisisCvDTO revisado) {
        List<AnalisisCvDTO.FormacionItemDTO> origFormacion = original.formacionAcademica() != null ? original.formacionAcademica() : List.of();
        List<AnalisisCvDTO.FormacionItemDTO> revFormacion = revisado.formacionAcademica() != null ? revisado.formacionAcademica() : List.of();
        List<AnalisisCvDTO.FormacionItemDTO> formacionLimpia = new ArrayList<>();
        for (int i = 0; i < revFormacion.size(); i++) {
            AnalisisCvDTO.FormacionItemDTO item = revFormacion.get(i);
            AnalisisCvDTO.FormacionItemDTO base = i < origFormacion.size() ? origFormacion.get(i) : null;
            boolean cambiado = base == null
                    || !Objects.equals(base.titulo(), item.titulo())
                    || !Objects.equals(base.institucion(), item.institucion())
                    || !Objects.equals(base.periodo(), item.periodo())
                    || !Objects.equals(base.estadoAcademico(), item.estadoAcademico());
            AnalisisCvDTO.EvidenciaDTO ev = sanitizarEvidencia(base != null ? base.evidencia() : null, item.evidencia(), cambiado);
            String estadoItem = cambiado ? MODIFICADO_ANALISTA : normalizarEstadoRevisionItem(item.estadoRevisionItem(), base.estadoRevisionItem());
            formacionLimpia.add(new AnalisisCvDTO.FormacionItemDTO(
                    item.titulo(), item.institucion(), item.periodo(), item.estadoAcademico(), ev, estadoItem));
        }

        List<AnalisisCvDTO.ExperienciaItemDTO> origExp = original.experienciasLaborales() != null ? original.experienciasLaborales() : List.of();
        List<AnalisisCvDTO.ExperienciaItemDTO> revExp = revisado.experienciasLaborales() != null ? revisado.experienciasLaborales() : List.of();
        List<AnalisisCvDTO.ExperienciaItemDTO> expLimpia = new ArrayList<>();
        for (int i = 0; i < revExp.size(); i++) {
            AnalisisCvDTO.ExperienciaItemDTO item = revExp.get(i);
            AnalisisCvDTO.ExperienciaItemDTO base = i < origExp.size() ? origExp.get(i) : null;
            boolean cambiado = base == null
                    || !Objects.equals(base.cargo(), item.cargo())
                    || !Objects.equals(base.empresa(), item.empresa())
                    || !Objects.equals(base.periodo(), item.periodo())
                    || !Objects.equals(base.duracionCalculada(), item.duracionCalculada())
                    || !Objects.equals(base.funciones(), item.funciones());
            AnalisisCvDTO.EvidenciaDTO ev = sanitizarEvidencia(base != null ? base.evidencia() : null, item.evidencia(), cambiado);
            String estadoItem = cambiado ? MODIFICADO_ANALISTA : normalizarEstadoRevisionItem(item.estadoRevisionItem(), base.estadoRevisionItem());
            expLimpia.add(new AnalisisCvDTO.ExperienciaItemDTO(
                    item.cargo(), item.empresa(), item.periodo(), item.duracionCalculada(), item.funciones(), ev, estadoItem));
        }

        List<AnalisisCvDTO.HabilidadItemDTO> origHab = original.habilidadesTecnicas() != null ? original.habilidadesTecnicas() : List.of();
        List<AnalisisCvDTO.HabilidadItemDTO> revHab = revisado.habilidadesTecnicas() != null ? revisado.habilidadesTecnicas() : List.of();
        List<AnalisisCvDTO.HabilidadItemDTO> habLimpia = new ArrayList<>();
        for (int i = 0; i < revHab.size(); i++) {
            AnalisisCvDTO.HabilidadItemDTO item = revHab.get(i);
            AnalisisCvDTO.HabilidadItemDTO base = i < origHab.size() ? origHab.get(i) : null;
            boolean cambiado = base == null
                    || !Objects.equals(base.nombre(), item.nombre())
                    || !Objects.equals(base.contextoUso(), item.contextoUso());
            AnalisisCvDTO.EvidenciaDTO ev = sanitizarEvidencia(base != null ? base.evidencia() : null, item.evidencia(), cambiado);
            String estadoItem = cambiado ? MODIFICADO_ANALISTA : normalizarEstadoRevisionItem(item.estadoRevisionItem(), base.estadoRevisionItem());
            habLimpia.add(new AnalisisCvDTO.HabilidadItemDTO(item.nombre(), item.contextoUso(), ev, estadoItem));
        }

        List<AnalisisCvDTO.CompetenciaItemDTO> origComp = original.competenciasDeclaradas() != null ? original.competenciasDeclaradas() : List.of();
        List<AnalisisCvDTO.CompetenciaItemDTO> revComp = revisado.competenciasDeclaradas() != null ? revisado.competenciasDeclaradas() : List.of();
        List<AnalisisCvDTO.CompetenciaItemDTO> compLimpia = new ArrayList<>();
        for (int i = 0; i < revComp.size(); i++) {
            AnalisisCvDTO.CompetenciaItemDTO item = revComp.get(i);
            AnalisisCvDTO.CompetenciaItemDTO base = i < origComp.size() ? origComp.get(i) : null;
            boolean cambiado = base == null || !Objects.equals(base.nombre(), item.nombre());
            AnalisisCvDTO.EvidenciaDTO ev = sanitizarEvidencia(base != null ? base.evidencia() : null, item.evidencia(), cambiado);
            String estadoItem = cambiado ? MODIFICADO_ANALISTA : normalizarEstadoRevisionItem(item.estadoRevisionItem(), base.estadoRevisionItem());
            compLimpia.add(new AnalisisCvDTO.CompetenciaItemDTO(item.nombre(), ev, estadoItem));
        }

        List<AnalisisCvDTO.CertificacionItemDTO> origCert = original.certificacionesYLicencias() != null ? original.certificacionesYLicencias() : List.of();
        List<AnalisisCvDTO.CertificacionItemDTO> revCert = revisado.certificacionesYLicencias() != null ? revisado.certificacionesYLicencias() : List.of();
        List<AnalisisCvDTO.CertificacionItemDTO> certLimpia = new ArrayList<>();
        for (int i = 0; i < revCert.size(); i++) {
            AnalisisCvDTO.CertificacionItemDTO item = revCert.get(i);
            AnalisisCvDTO.CertificacionItemDTO base = i < origCert.size() ? origCert.get(i) : null;
            boolean cambiado = base == null
                    || !Objects.equals(base.nombre(), item.nombre())
                    || !Objects.equals(base.entidadEmisora(), item.entidadEmisora())
                    || !Objects.equals(base.vigencia(), item.vigencia());
            AnalisisCvDTO.EvidenciaDTO ev = sanitizarEvidencia(base != null ? base.evidencia() : null, item.evidencia(), cambiado);
            String estadoItem = cambiado ? MODIFICADO_ANALISTA : normalizarEstadoRevisionItem(item.estadoRevisionItem(), base.estadoRevisionItem());
            certLimpia.add(new AnalisisCvDTO.CertificacionItemDTO(item.nombre(), item.entidadEmisora(), item.vigencia(), ev, estadoItem));
        }

        // Matriz de requisitos: conservar exclusivamente IDs reales y obligatoriedad del análisis oficial
        Map<Long, AnalisisCvDTO.RequisitoMatrizItemDTO> origMatrizPorId = new LinkedHashMap<>();
        if (original.matrizRequisitos() != null) {
            for (AnalisisCvDTO.RequisitoMatrizItemDTO r : original.matrizRequisitos()) {
                if (r.requisitoId() != null) {
                    origMatrizPorId.put(r.requisitoId(), r);
                }
            }
        }
        List<AnalisisCvDTO.RequisitoMatrizItemDTO> matrizLimpia = new ArrayList<>();
        if (revisado.matrizRequisitos() != null) {
            for (AnalisisCvDTO.RequisitoMatrizItemDTO item : revisado.matrizRequisitos()) {
                AnalisisCvDTO.RequisitoMatrizItemDTO base = item.requisitoId() != null
                        ? origMatrizPorId.get(item.requisitoId())
                        : null;
                if (base == null && !origMatrizPorId.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Se detectó un requisito no autorizado o inexistente en la matriz oficial.");
                }
                boolean cambiado = base != null && (
                        !Objects.equals(base.resultadoAnalisis(), item.resultadoAnalisis())
                                || !Objects.equals(base.observacionEvidencia(), item.observacionEvidencia()));
                AnalisisCvDTO.EvidenciaDTO ev = sanitizarEvidencia(base != null ? base.evidencia() : null, item.evidencia(), cambiado);
                String estadoItem = cambiado
                        ? MODIFICADO_ANALISTA
                        : normalizarEstadoRevisionItem(item.estadoRevisionItem(), base != null ? base.estadoRevisionItem() : null);

                matrizLimpia.add(new AnalisisCvDTO.RequisitoMatrizItemDTO(
                        base != null ? base.requisitoId() : item.requisitoId(),
                        base != null ? base.requisito() : item.requisito(),
                        base != null ? base.obligatorio() : item.obligatorio(),
                        item.resultadoAnalisis(),
                        item.observacionEvidencia(),
                        ev,
                        estadoItem
                ));
            }
        }

        return new AnalisisCvDTO(
                revisado.nombreDetectado(),
                revisado.tituloProfesional(),
                revisado.resumenProfesionalObjetivo(),
                formacionLimpia,
                expLimpia,
                habLimpia,
                compLimpia,
                certLimpia,
                matrizLimpia,
                revisado.datosFaltantesOAmbiguos() != null ? revisado.datosFaltantesOAmbiguos() : List.of()
        );
    }

    private AnalisisCvDTO.EvidenciaDTO sanitizarEvidencia(
            AnalisisCvDTO.EvidenciaDTO originalEv,
            AnalisisCvDTO.EvidenciaDTO revisadaEv,
            boolean itemEditadoPorAnalista) {
        if (revisadaEv == null) {
            return originalEv;
        }
        String estadoOriginal = originalEv != null ? originalEv.estadoVerificacion() : null;
        String estadoPropuesto = revisadaEv.estadoVerificacion();

        // Una edición manual jamás convierte por sí sola una evidencia no verificada en VERIFICADA_TEXTUAL
        boolean originalmenteVerificada = VERIFICADA_TEXTUAL.equalsIgnoreCase(estadoOriginal)
                && Objects.equals(originalEv.fragmento(), revisadaEv.fragmento())
                && Objects.equals(originalEv.pagina(), revisadaEv.pagina());

        String estadoFinal;
        if (VERIFICADA_TEXTUAL.equalsIgnoreCase(estadoPropuesto) && !originalmenteVerificada) {
            estadoFinal = (estadoOriginal != null && !estadoOriginal.isBlank()) ? estadoOriginal : REVISION_HUMANA;
        } else if (itemEditadoPorAnalista && !originalmenteVerificada) {
            estadoFinal = (estadoOriginal != null && !estadoOriginal.isBlank()) ? estadoOriginal : REVISION_HUMANA;
        } else {
            estadoFinal = estadoPropuesto != null ? estadoPropuesto : estadoOriginal;
        }

        return new AnalisisCvDTO.EvidenciaDTO(
                revisadaEv.pagina(),
                revisadaEv.fragmento(),
                revisadaEv.tipoAntecedente(),
                estadoFinal
        );
    }

    private String normalizarEstadoRevisionItem(String propuesto, String respaldo) {
        if (propuesto != null && !propuesto.isBlank()) {
            return propuesto;
        }
        return (respaldo != null && !respaldo.isBlank()) ? respaldo : "PENDIENTE";
    }

    private void verificarRepositoriosPostulacion() {
        if (postulacionesRepo == null || documentosPostulacionRepo == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Los repositorios de postulaciones son obligatorios y no fueron inicializados.");
        }
    }

    private AnalisisCvResponseDTO mapearAResponsePostulacion(AnalisisCv a, Postulacion p) {
        try {
            AnalisisCvDTO original = objectMapper.readValue(a.getResultadoIaJson(), AnalisisCvDTO.class);
            AnalisisCvDTO actual = a.getDatosValidadosJson() != null
                    ? objectMapper.readValue(a.getDatosValidadosJson(), AnalisisCvDTO.class)
                    : original;
            PreguntasApoyoDTO preguntas = (a.getPreguntasApoyoJson() != null && !a.getPreguntasApoyoJson().isBlank())
                    ? objectMapper.readValue(a.getPreguntasApoyoJson(), PreguntasApoyoDTO.class)
                    : null;

            return new AnalisisCvResponseDTO(
                    a.getId(),
                    p.getId(),
                    p.getCandidato().getId(),
                    p.getCandidato().getNombre(),
                    p.getOfertaLaboral().getCargo(),
                    p.getOfertaLaboral().getCategoria(),
                    a.getNombreArchivoAnalizado(),
                    a.getHashDocumento(),
                    a.getModeloUtilizado(),
                    a.getFechaGeneracion(),
                    a.getEstadoAnalisis(),
                    a.getEstadoRevision(),
                    a.getUsuarioValidador(),
                    a.getFechaValidacion(),
                    a.getObservacionesAnalista(),
                    original,
                    actual,
                    preguntas
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo reconstruir el historial del análisis documental.");
        }
    }

    private AnalisisCvResponseDTO mapearAResponse(AnalisisCv a, Candidato c) {
        try {
            AnalisisCvDTO original = objectMapper.readValue(a.getResultadoIaJson(), AnalisisCvDTO.class);
            AnalisisCvDTO actual = a.getDatosValidadosJson() != null
                    ? objectMapper.readValue(a.getDatosValidadosJson(), AnalisisCvDTO.class)
                    : original;
            PreguntasApoyoDTO preguntas = (a.getPreguntasApoyoJson() != null && !a.getPreguntasApoyoJson().isBlank())
                    ? objectMapper.readValue(a.getPreguntasApoyoJson(), PreguntasApoyoDTO.class)
                    : null;

            return new AnalisisCvResponseDTO(
                    a.getId(),
                    a.getPostulacionId(),
                    a.getCandidatoId(),
                    c.getNombre(),
                    c.getCargoPostula(),
                    c.getFamiliaCargo(),
                    a.getNombreArchivoAnalizado(),
                    a.getHashDocumento(),
                    a.getModeloUtilizado(),
                    a.getFechaGeneracion(),
                    a.getEstadoAnalisis(),
                    a.getEstadoRevision(),
                    a.getUsuarioValidador(),
                    a.getFechaValidacion(),
                    a.getObservacionesAnalista(),
                    original,
                    actual,
                    preguntas
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo reconstruir el historial del análisis documental.");
        }
    }

    private record PostulacionSnapshot(
            Long postulacionId,
            Long documentoPostulacionId,
            Long candidatoId,
            Long ofertaId,
            String nombreCandidato,
            String cargoOferta,
            String categoriaOferta,
            String nombreArchivo,
            String tipoContenido,
            byte[] contenido,
            String hashSha256
    ) {}

    private record DocumentoSnapshot(
            Long documentoId,
            Long candidatoId,
            String nombreCandidato,
            String cargoPostula,
            String familiaCargo,
            String nombreArchivo,
            String tipoContenido,
            byte[] contenido,
            String hashSha256
    ) {}

    private record SolicitudContexto(
            Long analisisId,
            String cargo,
            String observacionesAnalista,
            AnalisisCvDTO datosValidados
    ) {}

    private record AnalisisResuelto(
            AnalisisCv analisis,
            Postulacion postulacion,
            String cargoEfectivo
    ) {}
}