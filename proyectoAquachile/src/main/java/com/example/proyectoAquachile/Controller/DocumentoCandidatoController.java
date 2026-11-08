package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Candidato;
import com.example.proyectoAquachile.Model.DocumentoCandidato;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import com.example.proyectoAquachile.Repository.DocumentoCandidatoRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Archivos de CV reales: el Analista los sube/reemplaza/elimina;
 * ambos roles pueden consultar metadatos y descargar el documento.
 * Se almacena en MySQL; no se utiliza el sistema de archivos del servidor.
 */
@RestController
@RequestMapping("/api/documentos/candidatos")
public class DocumentoCandidatoController {
    private static final long MAX_BYTES = 5L * 1024L * 1024L;
    private final DocumentoCandidatoRepository documentos;
    private final CandidatoRepository candidatos;

    public DocumentoCandidatoController(DocumentoCandidatoRepository documentos, CandidatoRepository candidatos) {
        this.documentos = documentos;
        this.candidatos = candidatos;
    }

    public record DocumentoInfo(Long id, Long candidatoId, String nombreArchivo,
                                String tipoContenido, long tamanoBytes, LocalDateTime fechaCarga) { }

    private DocumentoInfo aInfo(DocumentoCandidato doc) {
        return new DocumentoInfo(doc.getId(), doc.getCandidato().getId(), doc.getNombreArchivo(),
                doc.getTipoContenido(), doc.getTamanoBytes(), doc.getFechaCarga());
    }

    private DocumentoCandidato obtener(Long candidatoId) {
        return documentos.findByCandidato_Id(candidatoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay CV adjunto a este candidato"));
    }

    @GetMapping("/{candidatoId}")
    @Transactional(readOnly = true)
    public DocumentoInfo consultar(@PathVariable Long candidatoId) {
        return aInfo(obtener(candidatoId));
    }

    @GetMapping("/{candidatoId}/descargar")
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

    @PostMapping(path = "/{candidatoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

        DocumentoCandidato documento = documentos.findByCandidato_Id(candidatoId)
                .orElseGet(DocumentoCandidato::new);
        documento.setCandidato(candidato);
        documento.setNombreArchivo(nombre);
        documento.setTipoContenido(tipo);
        documento.setTamanoBytes(contenido.length);
        documento.setContenido(contenido);
        documento.setFechaCarga(LocalDateTime.now());
        return aInfo(documentos.save(documento));
    }

    @DeleteMapping("/{candidatoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminar(@PathVariable Long candidatoId) {
        documentos.delete(obtener(candidatoId));
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

    /** Verifica la firma del archivo: la extensión sola no es suficiente. */
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
