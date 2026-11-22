package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvResponseDTO;
import com.example.proyectoAquachile.Dto.PreguntasApoyoDTO;
import com.example.proyectoAquachile.Dto.ValidacionAnalisisRequestDTO;
import com.example.proyectoAquachile.Model.*;
import com.example.proyectoAquachile.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GestionAnalisisCvService {

    private final DocumentoCandidatoRepository documentos;
    private final AnalisisCvRepository analisisRepo;
    private final RequisitoCargoRepository requisitosRepo;
    private final SolicitudEvaluacionRepository solicitudesRepo;
    private final CvAnalisisService cvAnalisisService;
    private final ObjectMapper objectMapper;
    private PostulacionRepository postulacionesRepo;
    private DocumentoPostulacionRepository documentosPostulacionRepo;

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
    }

    @Autowired(required = false)
    public void setPostulacionesRepo(PostulacionRepository postulacionesRepo) {
        this.postulacionesRepo = postulacionesRepo;
    }

    @Autowired(required = false)
    public void setDocumentosPostulacionRepo(DocumentoPostulacionRepository documentosPostulacionRepo) {
        this.documentosPostulacionRepo = documentosPostulacionRepo;
    }

    // =========================================================================
    // FLUJO PRINCIPAL VINCULADO A POSTULACIÓN (postulacionId)
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisActivoPorPostulacion(Long postulacionId, boolean soloValidado) {
        if (postulacionesRepo == null || documentosPostulacionRepo == null) {
            return Optional.empty();
        }
        Postulacion postulacion = postulacionesRepo.findById(postulacionId).orElse(null);
        DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(postulacionId).orElse(null);
        if (postulacion == null || doc == null) {
            return Optional.empty();
        }

        String hashActual = doc.getHashSha256();
        return analisisRepo.findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        postulacionId, AnalisisCv.ANALISIS_GENERADO)
                .filter(a -> hashActual.equals(a.getHashDocumento()))
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

        // Llamada externa a Groq fuera de cualquier transacción de base de datos
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
        if (postulacionesRepo == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Repositorio de postulaciones no disponible.");
        }

        Postulacion postulacion = postulacionesRepo.findById(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));

        AnalisisCv analisis = analisisRepo.findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        postulacionId, AnalisisCv.ANALISIS_GENERADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un análisis activo para revisar en esta postulación."));

        try {
            analisis.setDatosValidadosJson(objectMapper.writeValueAsString(request.datosRevisados()));
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
        if (postulacionesRepo == null || documentosPostulacionRepo == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Módulo de postulaciones no inicializado.");
        }
        Postulacion p = postulacionesRepo.findById(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        DocumentoPostulacion doc = documentosPostulacionRepo.findByPostulacion_Id(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación no tiene CV adjunto."));

        return new PostulacionSnapshot(
                p.getId(),
                doc.getId(),
                p.getCandidato().getId(),
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
        invalidarAnalisisPreviosPorPostulacion(snapshot.postulacionId());

        Postulacion postulacionRef = postulacionesRepo.findById(snapshot.postulacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación fue eliminada durante el análisis."));
        DocumentoPostulacion docRef = documentosPostulacionRepo.findById(snapshot.documentoPostulacionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El documento fue eliminado durante el análisis."));

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
    // COMPATIBILIDAD CON CONSULTAS POR SOLICITUD Y CANDIDATO LEGACY
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
                .filter(a -> hashActual.equals(a.getHashDocumento()))
                .filter(a -> !soloValidado || AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                .map(a -> mapearAResponse(a, doc.getCandidato()));
    }

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisValidadoPorSolicitud(Long solicitudId) {
        SolicitudEvaluacion solicitud = solicitudesRepo.findById(solicitudId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de evaluación no encontrada"));

        Long candidatoId = solicitud.getCandidato().getId();
        if (postulacionesRepo != null) {
            List<Postulacion> postulaciones = postulacionesRepo.findByCandidato_IdOrderByFechaPostulacionDesc(candidatoId);
            for (Postulacion p : postulaciones) {
                if (solicitud.getCargo() != null
                        && solicitud.getCargo().equalsIgnoreCase(p.getOfertaLaboral().getCargo())) {
                    Optional<AnalisisCvResponseDTO> dePostulacion = obtenerAnalisisActivoPorPostulacion(p.getId(), true);
                    if (dePostulacion.isPresent()) {
                        return dePostulacion;
                    }
                }
            }
            for (Postulacion p : postulaciones) {
                Optional<AnalisisCvResponseDTO> dePostulacion = obtenerAnalisisActivoPorPostulacion(p.getId(), true);
                if (dePostulacion.isPresent()) {
                    return dePostulacion;
                }
            }
        }

        return obtenerAnalisisActivo(candidatoId, true);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un análisis activo para revisar. Genera primero el análisis del CV."));

        try {
            analisis.setDatosValidadosJson(objectMapper.writeValueAsString(request.datosRevisados()));
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
        for (AnalisisCv item : vigentes) {
            if (item.getPostulacionId() == null) {
                item.setEstadoAnalisis(AnalisisCv.ANALISIS_INVALIDADO);
            }
        }
        if (!vigentes.isEmpty()) {
            analisisRepo.saveAll(vigentes);
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
        invalidarAnalisisPrevios(snapshot.candidatoId());

        DocumentoCandidato docRef = documentos.findById(snapshot.documentoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El documento fue eliminado durante el análisis."));

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
        Long candidatoId = sol.getCandidato().getId();

        AnalisisCv analisis = null;
        if (postulacionesRepo != null) {
            for (Postulacion p : postulacionesRepo.findByCandidato_IdOrderByFechaPostulacionDesc(candidatoId)) {
                Optional<AnalisisCv> opt = analisisRepo
                        .findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                                p.getId(), AnalisisCv.ANALISIS_GENERADO)
                        .filter(a -> AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()));
                if (opt.isPresent()) {
                    analisis = opt.get();
                    break;
                }
            }
        }

        if (analisis == null) {
            analisis = analisisRepo.findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                            candidatoId, AnalisisCv.ANALISIS_GENERADO)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El candidato aún no cuenta con un análisis de CV validado por el analista."));
        }

        if (!AnalisisCv.REVISION_VALIDADO.equals(analisis.getEstadoRevision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El análisis del CV aún está pendiente de validación por parte del analista.");
        }

        try {
            String jsonFuente = analisis.getDatosValidadosJson() != null
                    ? analisis.getDatosValidadosJson()
                    : analisis.getResultadoIaJson();
            AnalisisCvDTO datos = objectMapper.readValue(jsonFuente, AnalisisCvDTO.class);
            String cargo = sol.getCargo() != null && !sol.getCargo().isBlank()
                    ? sol.getCargo()
                    : sol.getCandidato().getCargoPostula();
            return new SolicitudContexto(analisis.getId(), cargo, analisis.getObservacionesAnalista(), datos);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error leyendo los antecedentes validados.");
        }
    }

    @Transactional
    protected void guardarPreguntasEnAnalisis(Long analisisId, PreguntasApoyoDTO preguntas) {
        analisisRepo.findById(analisisId).ifPresent(analisis -> {
            try {
                analisis.setPreguntasApoyoJson(objectMapper.writeValueAsString(preguntas));
                analisisRepo.save(analisis);
            } catch (Exception ignored) {
            }
        });
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
}