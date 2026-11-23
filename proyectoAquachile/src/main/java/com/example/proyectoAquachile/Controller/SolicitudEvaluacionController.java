package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Postulacion;
import com.example.proyectoAquachile.Model.SolicitudEvaluacion;
import com.example.proyectoAquachile.Repository.PostulacionRepository;
import com.example.proyectoAquachile.Repository.SolicitudEvaluacionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudEvaluacionController {

    private final SolicitudEvaluacionRepository repository;
    private final PostulacionRepository postulacionRepository;

    public SolicitudEvaluacionController(
            SolicitudEvaluacionRepository repository,
            PostulacionRepository postulacionRepository) {
        this.repository = repository;
        this.postulacionRepository = postulacionRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SolicitudEvaluacion> obtenerTodas() {
        return repository.findAll();
    }

    @PostMapping
    @Transactional
    public SolicitudEvaluacion crearSolicitud(@RequestBody SolicitudEvaluacion solicitud) {
        if (solicitud.getCandidato() == null || solicitud.getCandidato().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes indicar el candidato de la solicitud.");
        }

        Long postulacionId = solicitud.getPostulacionId();
        if (postulacionId != null) {
            Postulacion postulacion = postulacionRepository.findById(postulacionId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "La postulación indicada no existe."));

            if (!postulacion.getCandidato().getId().equals(solicitud.getCandidato().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "La postulación no pertenece al candidato seleccionado.");
            }

            solicitud.setPostulacion(postulacion);
            if (solicitud.getCargo() == null || solicitud.getCargo().isBlank()) {
                solicitud.setCargo(postulacion.getOfertaLaboral().getCargo());
            }
            if (solicitud.getFamiliaCargo() == null || solicitud.getFamiliaCargo().isBlank()) {
                solicitud.setFamiliaCargo(postulacion.getOfertaLaboral().getCategoria());
            }
        }

        return repository.save(solicitud);
    }

    @PutMapping("/{id}/estado")
    @Transactional
    public SolicitudEvaluacion actualizarEstado(@PathVariable Long id, @RequestBody String nuevoEstado) {
        SolicitudEvaluacion solicitud = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada."));
        solicitud.setEstado(nuevoEstado);
        return repository.save(solicitud);
    }
}