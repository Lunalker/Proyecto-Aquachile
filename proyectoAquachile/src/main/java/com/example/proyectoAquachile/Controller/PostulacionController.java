package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Dto.AnalisisCvResponseDTO;
import com.example.proyectoAquachile.Dto.ValidacionAnalisisRequestDTO;
import com.example.proyectoAquachile.Model.DocumentoPostulacion;
import com.example.proyectoAquachile.Service.GestionAnalisisCvService;
import com.example.proyectoAquachile.Service.PostulacionService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/postulaciones")
public class PostulacionController {

    private final PostulacionService postulacionService;
    private final GestionAnalisisCvService gestionAnalisisService;

    public PostulacionController(
            PostulacionService postulacionService,
            GestionAnalisisCvService gestionAnalisisService) {
        this.postulacionService = postulacionService;
        this.gestionAnalisisService = gestionAnalisisService;
    }

    /**
     * Endpoint público transaccional para postular a una oferta laboral publicada y adjuntar CV en una sola petición.
     */
    @PostMapping(path = "/publicas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostulacionService.PostulacionResumenDTO> crearPostulacionPublica(
            @RequestParam("ofertaId") Long ofertaId,
            @RequestParam("nombre") String nombre,
            @RequestParam("correo") String correo,
            @RequestParam("telefono") String telefono,
            @RequestParam(value = "ubicacion", required = false) String ubicacion,
            @RequestParam(value = "unidad", required = false) String unidad,
            @RequestPart("archivo") MultipartFile archivo) {

        PostulacionService.DatosPostulantePublico datos = new PostulacionService.DatosPostulantePublico(
                ofertaId, nombre, correo, telefono, ubicacion, unidad
        );
        PostulacionService.PostulacionResumenDTO creada = postulacionService.registrarPostulacionPublica(datos, archivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @GetMapping
    public List<PostulacionService.PostulacionResumenDTO> listarTodas() {
        return postulacionService.listarTodas();
    }

    @GetMapping("/candidato/{candidatoId}")
    public List<PostulacionService.PostulacionResumenDTO> listarPorCandidato(@PathVariable Long candidatoId) {
        return postulacionService.listarPorCandidato(candidatoId);
    }

    @GetMapping("/{id}")
    public PostulacionService.PostulacionResumenDTO obtenerPorId(@PathVariable Long id) {
        return postulacionService.obtenerResumen(id);
    }

    @GetMapping("/{id}/cv")
    public PostulacionService.DocumentoPostulacionMetaDTO obtenerMetadatosCv(@PathVariable Long id) {
        return postulacionService.obtenerMetadatosCv(id);
    }

    @GetMapping("/{id}/cv/descargar")
    public ResponseEntity<byte[]> descargarCv(@PathVariable Long id) {
        DocumentoPostulacion documento = postulacionService.obtenerDocumentoBinario(id);
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(documento.getNombreOriginal(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .contentType(MediaType.parseMediaType(documento.getTipoMime()))
                .contentLength(documento.getTamanoBytes())
                .body(documento.getContenido());
    }

    // --- Integración IA vinculada específicamente a postulacionId ---

    @GetMapping("/{id}/analisis")
    public ResponseEntity<AnalisisCvResponseDTO> obtenerAnalisisPostulacion(
            @PathVariable Long id,
            Authentication auth) {
        boolean esAnalista = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ANALISTA".equals(a.getAuthority()));
        return gestionAnalisisService.obtenerAnalisisActivoPorPostulacion(id, !esAnalista)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/analizar-ia")
    public ResponseEntity<AnalisisCvResponseDTO> analizarCvDePostulacion(
            @PathVariable Long id,
            @RequestParam(name = "forzar", defaultValue = "false") boolean forzar) {
        return ResponseEntity.ok(gestionAnalisisService.generarORecuperarAnalisisPorPostulacion(id, forzar));
    }

    @PutMapping("/{id}/validar")
    public ResponseEntity<AnalisisCvResponseDTO> validarAnalisisPostulacion(
            @PathVariable Long id,
            @RequestBody ValidacionAnalisisRequestDTO request,
            Authentication auth) {
        String usuario = auth != null ? auth.getName() : "analista";
        return ResponseEntity.ok(gestionAnalisisService.guardarRevisionOValidarPorPostulacion(id, request, usuario));
    }
}