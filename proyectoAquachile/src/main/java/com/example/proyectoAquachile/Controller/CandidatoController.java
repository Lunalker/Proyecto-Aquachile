package com.example.proyectoAquachile.Controller;

import com.example.proyectoAquachile.Model.Candidato;
import com.example.proyectoAquachile.Repository.CandidatoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/candidatos")
public class CandidatoController {

    @Autowired
    private CandidatoRepository candidatoRepository;

    @GetMapping
    public List<Candidato> obtenerTodos() {
        return candidatoRepository.findAll();
    }

    @GetMapping("/{id}")
    public Candidato obtenerPorId(@PathVariable Long id) {
        return candidatoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidato no encontrado"));
    }

    @PostMapping
    public Candidato guardarCandidato(@RequestBody Candidato candidato) {
        return candidatoRepository.save(candidato);
    }

    @PutMapping("/{id}")
    public Candidato actualizarCandidato(@PathVariable Long id, @RequestBody Candidato nuevosDatos) {
        Candidato existente = candidatoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidato no encontrado"));

        if (estaVacio(nuevosDatos.getNombre()) || estaVacio(nuevosDatos.getCorreo()) || estaVacio(nuevosDatos.getCargoPostula())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, correo y cargo son obligatorios");
        }
        if (!nuevosDatos.getCorreo().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de correo no valido");
        }

        // Se actualiza la entidad existente: nunca se crea un candidato nuevo desde este PUT.
        existente.setNombre(nuevosDatos.getNombre().trim());
        existente.setCorreo(nuevosDatos.getCorreo().trim());
        existente.setTelefono(nuevosDatos.getTelefono());
        existente.setCargoPostula(nuevosDatos.getCargoPostula().trim());
        existente.setFamiliaCargo(nuevosDatos.getFamiliaCargo());
        existente.setOrigen(nuevosDatos.getOrigen());
        existente.setUbicacion(nuevosDatos.getUbicacion());
        existente.setUnidad(nuevosDatos.getUnidad());

        return candidatoRepository.save(existente);
    }

    private boolean estaVacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }
}
