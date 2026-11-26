package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.OfertaLaboral;
import com.example.proyectoAquachile.Repository.OfertaLaboralRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ofertas")
public class OfertaLaboralController {

    private final OfertaLaboralRepository ofertaRepository;

    public OfertaLaboralController(OfertaLaboralRepository ofertaRepository) {
        this.ofertaRepository = ofertaRepository;
    }

    @GetMapping("/publicas")
    public List<OfertaLaboral> listarPublicas() {
        return ofertaRepository.findByEstadoPublicacionOrderByFechaPublicacionDesc(OfertaLaboral.ESTADO_PUBLICADA);
    }

    @GetMapping("/publicas/{id}")
    public OfertaLaboral obtenerPublica(@PathVariable Long id) {
        return ofertaRepository.findByIdAndEstadoPublicacion(id, OfertaLaboral.ESTADO_PUBLICADA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta laboral no disponible."));
    }

    @GetMapping
    public List<OfertaLaboral> listarTodas() {
        return ofertaRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<OfertaLaboral> crear(@RequestBody OfertaLaboral oferta) {
        if (oferta.getFechaPublicacion() == null) {
            oferta.setFechaPublicacion(LocalDateTime.now());
        }
        if (oferta.getEstadoPublicacion() == null || oferta.getEstadoPublicacion().isBlank()) {
            oferta.setEstadoPublicacion(OfertaLaboral.ESTADO_PUBLICADA);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ofertaRepository.save(oferta));
    }
}