package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Dto.AnalisisCvResponseDTO;
import com.example.proyectoAquachile.Dto.PreguntasApoyoDTO;
import com.example.proyectoAquachile.Dto.ValidacionAnalisisRequestDTO;
import com.example.proyectoAquachile.Model.Candidato;
import com.example.proyectoAquachile.Model.DocumentoCandidato;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import com.example.proyectoAquachile.Repository.DocumentoCandidatoRepository;
import com.example.proyectoAquachile.Service.GestionAnalisisCvService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gestión de documentos de postulantes y flujo asistido por IA con revisión humana.
 * El análisis de IA no mantiene transacciones abiertas durante llamadas externas y
 * invalida automáticamente versiones previas al reemplazar o eliminar el CV.
 */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoCandidatoController {

    private static final long MAX_BYTES = 5L * 1024L * 1024L;
    private final DocumentoCandidatoRepository documentos;
    private final CandidatoRepository candidatos;
    private final GestionAnalisisCvService gestionAnalisisService;

    public DocumentoCandidatoController(
            DocumentoCandidatoRepository documentos,
            CandidatoRepository candidatos,
            GestionAnalisisCvService gestionAnalisisService) {
        this.documentos = documentos;
        this.candidatos = candidatos;
        this.gestionAnalisisService = gestionAnalisisService;
    }

    public record DocumentoInfo(
            Long id,
            Long candidatoId,
            String nombreArchivo,
            String tipoContenido,
            long tamanoBytes,
            LocalDateTime fechaCarga,
            boolean analizableIa,
            String motivoNoAnalizable
    ) {}

    private DocumentoInfo aInfo(DocumentoCandidato doc) {
        boolean esPdf = "application/pdf".equalsIgnoreCase(doc.getTipoContenido());
        String motivo = esPdf ? null
                : "El análisis con IA admite únicamente documentos PDF auténticos. Los archivos Word (DOC/DOCX) deben revisarse manualmente o subirse en PDF.";
        return new DocumentoInfo(
                doc.getId(),
                doc.getCandidato().getId(),
                doc.getNombreArchivo(),
                doc.getTipoContenido(),
                doc.getTamanoBytes(),
                doc.getFechaCarga(),
                esPdf,
                motivo
        );
    }

    private DocumentoCandidato obtener(Long candidatoId) {
        return documentos.findByCandidato_Id(candidatoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay CV adjunto a este candidato"));
    }

    @GetMapping("/candidatos/{candidatoId}")
    @Transactional(readOnly = true)
    public DocumentoInfo consultar(@PathVariable Long candidatoId) {
        return aInfo(obtener(candidatoId));
    }

    @GetMapping("/candidatos/{candidatoId}/descargar")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargar(@PathVariable Long candidatoId) {
        DocumentoCandidato documento = obtener(candidatoId);
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(documento.getNombreArchivo(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(documento.getTipoContenido()))
                .contentLength(documento.getTamanoBytes())
                .body(documento.getContenido());
    }

    @PostMapping(path = "/candidatos/{candidatoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public DocumentoInfo adjuntar(@PathVariable Long candidatoId, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        Candidato candidato = candidatos.findById(candidatoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidato inexistente"));
        if (archivo.isEmpty() || archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CV debe ocupar entre 1 byte y 5 MB");
        }
        String nombre = nombreSeguro(archivo.getOriginalFilename());
        String extension = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] contenido = archivo.getBytes();
        String tipo = detectarTipo(extension, contenido);

        // Invalida cualquier análisis previo antes de reemplazar el archivo
        gestionAnalisisService.invalidarAnalisisPrevios(candidatoId);

        DocumentoCandidato documento = documentos.findByCandidato_Id(candidatoId)
                .orElseGet(DocumentoCandidato::new);
        documento.setCandidato(candidato);
        documento.setNombreArchivo(nombre);
        documento.setTipoContenido(tipo);
        documento.setTamanoBytes(contenido.length);
        documento.setContenido(contenido);
        documento.setFechaCarga(LocalDateTime.now());
        documento.setAnalisisIa(null);
        return aInfo(documentos.save(documento));
    }

    @DeleteMapping("/candidatos/{candidatoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminar(@PathVariable Long candidatoId) {
        gestionAnalisisService.invalidarAnalisisPrevios(candidatoId);
        documentos.delete(obtener(candidatoId));
    }

    // --- Endpoints de Análisis Documental con IA y Revisión Humana ---

    @GetMapping("/candidatos/{candidatoId}/analisis")
    public ResponseEntity<AnalisisCvResponseDTO> obtenerAnalisis(
            @PathVariable Long candidatoId,
            Authentication auth) {
        boolean esAnalista = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ANALISTA".equals(a.getAuthority()));
        return gestionAnalisisService.obtenerAnalisisActivo(candidatoId, !esAnalista)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/candidatos/{candidatoId}/analizar-ia")
    public ResponseEntity<AnalisisCvResponseDTO> analizarDocumentoConIa(
            @PathVariable Long candidatoId,
            @RequestParam(name = "forzar", defaultValue = "false") boolean forzar) {
        AnalisisCvResponseDTO resultado = gestionAnalisisService.generarORecuperarAnalisis(candidatoId, forzar);
        return ResponseEntity.ok(resultado);
    }

    @PutMapping("/candidatos/{candidatoId}/validar")
    public ResponseEntity<AnalisisCvResponseDTO> revisarOValidarAnalisis(
            @PathVariable Long candidatoId,
            @RequestBody ValidacionAnalisisRequestDTO request,
            Authentication auth) {
        String usuario = auth != null ? auth.getName() : "analista";
        AnalisisCvResponseDTO actualizado = gestionAnalisisService.guardarRevisionOValidar(
                candidatoId, request, usuario);
        return ResponseEntity.ok(actualizado);
    }

    @GetMapping("/solicitudes/{solicitudId}/analisis-validado")
    public ResponseEntity<AnalisisCvResponseDTO> obtenerAnalisisValidadoParaEvaluador(
            @PathVariable Long solicitudId) {
        return gestionAnalisisService.obtenerAnalisisValidadoPorSolicitud(solicitudId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/solicitudes/{solicitudId}/preguntas-apoyo")
    public ResponseEntity<PreguntasApoyoDTO> generarPreguntasApoyo(
            @PathVariable Long solicitudId) {
        return ResponseEntity.ok(gestionAnalisisService.generarPreguntasApoyoParaSolicitud(solicitudId));
    }

    private static String nombreSeguro(String original) {
        if (original == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el nombre del archivo");
        }
        String normalizado = original.replace('\\', '/');
        String nombre = normalizado.substring(normalizado.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_").trim();
        if (nombre.isBlank() || nombre.length() > 255 || !nombre.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre del archivo inválido");
        }
        return nombre;
    }

    /** Verifica la firma binaria real del archivo: la extensión sola no es suficiente. */
    private static String detectarTipo(String extension, byte[] bytes) {
        if (extension.equals("pdf") && bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') {
            return "application/pdf";
        }
        if (extension.equals("docx") && esDocx(bytes)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (extension.equals("doc") && bytes.length >= 8
                && (bytes[0] & 0xff) == 0xd0 && (bytes[1] & 0xff) == 0xcf
                && (bytes[2] & 0xff) == 0x11 && (bytes[3] & 0xff) == 0xe0
                && (bytes[4] & 0xff) == 0xa1 && (bytes[5] & 0xff) == 0xb1
                && (bytes[6] & 0xff) == 0x1a && (bytes[7] & 0xff) == 0xe1) {
            return "application/msword";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Formato no válido: se aceptan archivos PDF, DOC o DOCX auténticos");
    }

    private static boolean esDocx(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') return false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entrada;
            int cantidad = 0;
            boolean tieneDocumento = false;
            while ((entrada = zip.getNextEntry()) != null && ++cantidad <= 300) {
                if ("word/document.xml".equals(entrada.getName())) tieneDocumento = true;
                if (tieneDocumento) return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}