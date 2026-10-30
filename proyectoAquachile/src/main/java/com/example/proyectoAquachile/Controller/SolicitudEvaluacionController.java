package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.SolicitudEvaluacion;
import com.example.proyectoAquachile.Repository.SolicitudEvaluacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudEvaluacionController {
    @Autowired
    private SolicitudEvaluacionRepository repository;

    @GetMapping
    public List<SolicitudEvaluacion> obtenerTodas() { return repository.findAll(); }

    @PostMapping
    public SolicitudEvaluacion crearSolicitud(@RequestBody SolicitudEvaluacion solicitud) {
        return repository.save(solicitud);
    }

    @PutMapping("/{id}/estado")
    public SolicitudEvaluacion actualizarEstado(@PathVariable Long id, @RequestBody String nuevoEstado) {
        SolicitudEvaluacion solicitud = repository.findById(id).orElseThrow();
        solicitud.setEstado(nuevoEstado);
        return repository.save(solicitud);
    }
}
