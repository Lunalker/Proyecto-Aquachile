package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Evaluacion;
import com.example.proyectoAquachile.Repository.EvaluacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluaciones")
@CrossOrigin(origins = "*")
public class EvaluacionController {

    @Autowired
    private EvaluacionRepository repository;

    @GetMapping
    public List<Evaluacion> obtenerTodas() {
        return repository.findAll();
    }

    @PostMapping
    public Evaluacion guardarEvaluacion(@RequestBody Evaluacion evaluacion) {
        return repository.save(evaluacion);
    }
}