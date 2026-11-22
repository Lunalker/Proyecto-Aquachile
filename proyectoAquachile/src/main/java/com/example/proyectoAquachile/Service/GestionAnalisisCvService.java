package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvResponseDTO;
import com.example.proyectoAquachile.Dto.PreguntasApoyoDTO;
import com.example.proyectoAquachile.Dto.ValidacionAnalisisRequestDTO;
import com.example.proyectoAquachile.Model.*;
import com.example.proyectoAquachile.Repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orquesta el ciclo completo del análisis documental sin mantener transacciones de BD abiertas
 * durante las llamadas HTTP externas a la API de IA.
 */
@Service
public class GestionAnalisisCvService {

    private final DocumentoCandidatoRepository documentos;
    private final AnalisisCvRepository analisisRepo;
    private final RequisitoCargoRepository requisitosRepo;
    private final SolicitudEvaluacionRepository solicitudesRepo;
    private final CvAnalisisService cvAnalisisService;
    private final ObjectMapper objectMapper;

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

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisActivo(Long candidatoId, boolean soloValidado) {
        DocumentoCandidato doc = documentos.findByCandidato_Id(candidatoId).orElse(null);
        if (doc == null) {
            return Optional.empty();
        }
        String hashActual = AnonimizadorCvUtil.calcularSha256(doc.getContenido());

        return analisisRepo.findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        candidatoId, AnalisisCv.ANALISIS_GENERADO)
                .filter(a -> hashActual.equals(a.getHashDocumento()))
                .filter(a -> !soloValidado || AnalisisCv.REVISION_VALIDADO.equals(a.getEstadoRevision()))
                .map(a -> mapearAResponse(a, doc.getCandidato()));
    }

    @Transactional(readOnly = true)
    public Optional<AnalisisCvResponseDTO> obtenerAnalisisValidadoPorSolicitud(Long solicitudId) {
        SolicitudEvaluacion solicitud = solicitudesRepo.findById(solicitudId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de evaluación no encontrada"));
        return obtenerAnalisisActivo(solicitud.getCandidato().getId(), true);
    }

    /**
     * Ejecuta el análisis evitando llamadas innecesarias a Groq si ya existe uno vigente para el mismo hash,
     * salvo que el analista solicite explícitamente forzar el reanálisis.
     */
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

        // Llamada externa a Groq fuera de cualquier transacción de base de datos
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

    /**
     * Genera preguntas de apoyo para el EVALUADOR usando exclusivamente los antecedentes ya validados.
     */
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
            item.setEstadoAnalisis(AnalisisCv.ANALISIS_INVALIDADO);
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

        AnalisisCv analisis = analisisRepo.findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
                        candidatoId, AnalisisCv.ANALISIS_GENERADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El candidato aún no cuenta con un análisis de CV validado por el analista."));

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