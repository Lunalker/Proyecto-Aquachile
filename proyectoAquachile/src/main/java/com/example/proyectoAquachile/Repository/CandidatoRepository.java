package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.Candidato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidatoRepository extends JpaRepository<Candidato, Long> {
    Optional<Candidato> findByCorreoIgnoreCase(String correo);
}