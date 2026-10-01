package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Candidato;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidatos")
@CrossOrigin(origins = "*") // Muy importante: Esto evita bloqueos de seguridad cuando conectemos React
public class CandidatoController {

    @Autowired
    private CandidatoRepository candidatoRepository;

    // MÉTODO GET: Devuelve la lista de todos los candidatos (Para mostrar en tu Dashboard)
    @GetMapping
    public List<Candidato> obtenerTodos() {
        return candidatoRepository.findAll();
    }

    // MÉTODO POST: Guarda un nuevo candidato en la BD (Para el Formulario Público)
    @PostMapping
    public Candidato guardarCandidato(@RequestBody Candidato candidato) {
        return candidatoRepository.save(candidato);
    }
}