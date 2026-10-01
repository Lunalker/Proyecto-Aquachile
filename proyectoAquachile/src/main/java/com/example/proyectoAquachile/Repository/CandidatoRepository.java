package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.Candidato; // Importamos tu clase Candidato
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidatoRepository extends JpaRepository<Candidato, Long> {
    // ¡Al heredar de JpaRepository, Spring Boot ya sabe cómo hacer el CRUD completo!
}