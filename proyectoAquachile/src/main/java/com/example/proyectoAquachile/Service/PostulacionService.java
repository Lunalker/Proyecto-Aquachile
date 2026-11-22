package com.example.proyectoAquachile.Service;

import com.example.proyectoAquachile.Model.Candidato;
import com.example.proyectoAquachile.Model.DocumentoPostulacion;
import com.example.proyectoAquachile.Model.OfertaLaboral;
import com.example.proyectoAquachile.Model.Postulacion;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import com.example.proyectoAquachile.Repository.DocumentoPostulacionRepository;
import com.example.proyectoAquachile.Repository.OfertaLaboralRepository;
import com.example.proyectoAquachile.Repository.PostulacionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PostulacionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OfertaLaboralRepository ofertaRepository;
    private final CandidatoRepository candidatoRepository;
    private final PostulacionRepository postulacionRepository;
    private final DocumentoPostulacionRepository documentoPostulacionRepository;

    public PostulacionService(
            OfertaLaboralRepository ofertaRepository,
            CandidatoRepository candidatoRepository,
            PostulacionRepository postulacionRepository,
            DocumentoPostulacionRepository documentoPostulacionRepository) {
        this.ofertaRepository = ofertaRepository;
        this.candidatoRepository = candidatoRepository;
        this.postulacionRepository = postulacionRepository;
        this.documentoPostulacionRepository = documentoPostulacionRepository;
    }

    public record DatosPostulantePublico(
            Long ofertaId,
            String nombre,
            String correo,
            String telefono,
            String ubicacion,
            String unidad
    ) {}

    public record PostulacionResumenDTO(
            Long id,
            Long candidatoId,
            String candidatoNombre,
            String candidatoCorreo,
            String candidatoTelefono,
            Long ofertaId,
            String ofertaCargo,
            String ofertaCategoria,
            String ofertaUbicacion,
            LocalDateTime fechaPostulacion,
            String estado,
            String contenedorUuid,
            String nombreContenedor,
            boolean tieneCv,
            String nombreArchivoCv,
            String tipoMimeCv,
            Long tamanoBytesCv,
            String hashSha256Cv,
            boolean analizableIa
    ) {}

    public record DocumentoPostulacionMetaDTO(
            Long id,
            Long postulacionId,
            Long candidatoId,
            String candidatoNombre,
            String ofertaCargo,
            String ofertaCategoria,
            String contenedorUuid,
            String nombreContenedor,
            String nombreOriginal,
            String tipoMime,
            Long tamanoBytes,
            LocalDateTime fechaCarga,
            String hashSha256,
            boolean analizableIa,
            String motivoNoAnalizable
    ) {}

    @Transactional(rollbackFor = Exception.class)
    public PostulacionResumenDTO registrarPostulacionPublica(
            DatosPostulantePublico datos,
            MultipartFile archivoCv) {

        // 1. Validar información del postulante
        validarDatosPostulante(datos);

        // 2. Comprobar que la oferta exista y esté publicada
        OfertaLaboral oferta = ofertaRepository
                .findByIdAndEstadoPublicacion(datos.ofertaId(), OfertaLaboral.ESTADO_PUBLICADA)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La oferta laboral seleccionada no existe o no se encuentra publicada."
                ));

        // 3. Validar archivo CV (tamaño <= 5 MB, extensión, firma binaria auténtica, MIME detectado y nombre seguro)
        ValidadorArchivoCvUtil.ArchivoCvValidado cvValidado = ValidadorArchivoCvUtil.validar(archivoCv);

        // 4. Registrar o recuperar al candidato
        String correoNormalizado = datos.correo().trim().toLowerCase(Locale.ROOT);
        Candidato candidato = candidatoRepository.findByCorreoIgnoreCase(correoNormalizado)
                .orElseGet(Candidato::new);

        candidato.setNombre(datos.nombre().trim());
        candidato.setCorreo(correoNormalizado);
        candidato.setTelefono(datos.telefono() != null ? datos.telefono().trim() : null);
        candidato.setCargoPostula(oferta.getCargo());
        candidato.setFamiliaCargo(oferta.getCategoria());
        if (candidato.getOrigen() == null || candidato.getOrigen().isBlank()) {
            candidato.setOrigen("Externo");
        }
        if (datos.ubicacion() != null && !datos.ubicacion().isBlank()) {
            candidato.setUbicacion(datos.ubicacion().trim());
        } else if (candidato.getUbicacion() == null || candidato.getUbicacion().isBlank()) {
            candidato.setUbicacion(oferta.getUbicacion());
        }
        if (datos.unidad() != null && !datos.unidad().isBlank()) {
            candidato.setUnidad(datos.unidad().trim());
        }
        candidato = candidatoRepository.save(candidato);

        // 5. Prevenir postulaciones duplicadas del mismo candidato a la misma oferta
        if (postulacionRepository.existsByCandidato_IdAndOfertaLaboral_Id(candidato.getId(), oferta.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya registraste una postulación para esta oferta laboral."
            );
        }

        // 6. Crear la postulación y generar UUID + nombre descriptivo de contenedor único
        String contenedorUuid = UUID.randomUUID().toString();
        String nombreContenedor = Postulacion.generarNombreContenedorSeguro(candidato.getNombre(), contenedorUuid);

        Postulacion postulacion = new Postulacion();
        postulacion.setCandidato(candidato);
        postulacion.setOfertaLaboral(oferta);
        postulacion.setFechaPostulacion(LocalDateTime.now());
        postulacion.setEstado(Postulacion.ESTADO_RECIBIDA);
        postulacion.setContenedorUuid(contenedorUuid);
        postulacion.setNombreContenedor(nombreContenedor);

        // 7. Guardar el CV binario en DocumentoPostulacion (1:1 con Postulacion)
        DocumentoPostulacion doc = new DocumentoPostulacion();
        doc.setPostulacion(postulacion);
        doc.setNombreOriginal(cvValidado.nombreSeguro());
        doc.setTipoMime(cvValidado.tipoMimeDetectado());
        doc.setTamanoBytes(cvValidado.tamanoBytes());
        doc.setFechaCarga(LocalDateTime.now());
        doc.setHashSha256(cvValidado.hashSha256());
        doc.setContenido(cvValidado.contenido());
        postulacion.setDocumento(doc);

        try {
            Postulacion guardada = postulacionRepository.saveAndFlush(postulacion);
            return aResumen(guardada);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una postulación registrada para este candidato y oferta."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<PostulacionResumenDTO> listarTodas() {
        return postulacionRepository.findAllByOrderByFechaPostulacionDesc()
                .stream()
                .map(this::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostulacionResumenDTO> listarPorCandidato(Long candidatoId) {
        return postulacionRepository.findByCandidato_IdOrderByFechaPostulacionDesc(candidatoId)
                .stream()
                .map(this::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public PostulacionResumenDTO obtenerResumen(Long postulacionId) {
        Postulacion postulacion = postulacionRepository.findById(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        return aResumen(postulacion);
    }

    @Transactional(readOnly = true)
    public DocumentoPostulacionMetaDTO obtenerMetadatosCv(Long postulacionId) {
        Postulacion postulacion = postulacionRepository.findById(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        DocumentoPostulacion doc = documentoPostulacionRepository.findByPostulacion_Id(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación no tiene CV adjunto."));

        boolean esPdf = "application/pdf".equalsIgnoreCase(doc.getTipoMime());
        String motivo = esPdf ? null
                : "El análisis con IA admite únicamente documentos PDF auténticos. Los archivos Word (DOC/DOCX) deben revisarse manualmente.";

        return new DocumentoPostulacionMetaDTO(
                doc.getId(),
                postulacion.getId(),
                postulacion.getCandidato().getId(),
                postulacion.getCandidato().getNombre(),
                postulacion.getOfertaLaboral().getCargo(),
                postulacion.getOfertaLaboral().getCategoria(),
                postulacion.getContenedorUuid(),
                postulacion.getNombreContenedor(),
                doc.getNombreOriginal(),
                doc.getTipoMime(),
                doc.getTamanoBytes(),
                doc.getFechaCarga(),
                doc.getHashSha256(),
                esPdf,
                motivo
        );
    }

    @Transactional(readOnly = true)
    public DocumentoPostulacion obtenerDocumentoBinario(Long postulacionId) {
        if (!postulacionRepository.existsById(postulacionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada.");
        }
        return documentoPostulacionRepository.findByPostulacion_Id(postulacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La postulación no tiene CV adjunto."));
    }

    private void validarDatosPostulante(DatosPostulantePublico datos) {
        if (datos == null || datos.ofertaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar una oferta laboral válida.");
        }
        if (datos.nombre() == null || datos.nombre().trim().length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa el nombre completo del postulante (mínimo 3 caracteres).");
        }
        if (datos.correo() == null || !EMAIL_PATTERN.matcher(datos.correo().trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un correo electrónico válido.");
        }
        if (datos.telefono() == null || datos.telefono().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un teléfono de contacto válido.");
        }
    }

    private PostulacionResumenDTO aResumen(Postulacion p) {
        DocumentoPostulacion doc = p.getDocumento();
        boolean tieneCv = doc != null;
        boolean analizableIa = tieneCv && "application/pdf".equalsIgnoreCase(doc.getTipoMime());
        return new PostulacionResumenDTO(
                p.getId(),
                p.getCandidato().getId(),
                p.getCandidato().getNombre(),
                p.getCandidato().getCorreo(),
                p.getCandidato().getTelefono(),
                p.getOfertaLaboral().getId(),
                p.getOfertaLaboral().getCargo(),
                p.getOfertaLaboral().getCategoria(),
                p.getOfertaLaboral().getUbicacion(),
                p.getFechaPostulacion(),
                p.getEstado(),
                p.getContenedorUuid(),
                p.getNombreContenedor(),
                tieneCv,
                tieneCv ? doc.getNombreOriginal() : null,
                tieneCv ? doc.getTipoMime() : null,
                tieneCv ? doc.getTamanoBytes() : null,
                tieneCv ? doc.getHashSha256() : null,
                analizableIa
        );
    }
}