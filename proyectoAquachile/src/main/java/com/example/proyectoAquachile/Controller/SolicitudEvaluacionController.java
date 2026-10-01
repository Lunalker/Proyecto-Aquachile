package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.SolicitudEvaluacion;
import com.example.proyectoAquachile.Repository.SolicitudEvaluacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes")
@CrossOrigin(origins = "*")
public class SolicitudEvaluacionController {

    @Autowired
    private SolicitudEvaluacionRepository repository;

    // Obtener todas las solicitudes (Para listar en el Dashboard)
    @GetMapping
    public List<SolicitudEvaluacion> obtenerTodas() {
        return repository.findAll();
    }

    // Crear una nueva solicitud
    @PostMapping
    public SolicitudEvaluacion crearSolicitud(@RequestBody SolicitudEvaluacion solicitud) {
        return repository.save(solicitud);
    }

    // Actualizar el estado de una solicitud (Ej: de "Pendiente" a "En proceso")
    @PutMapping("/{id}/estado")
    public SolicitudEvaluacion actualizarEstado(@PathVariable Long id, @RequestBody String nuevoEstado) {
        // Buscamos la solicitud, actualizamos su estado y la guardamos
        SolicitudEvaluacion solicitud = repository.findById(id).orElseThrow();
        solicitud.setEstado(nuevoEstado);
        return repository.save(solicitud);
    }
}