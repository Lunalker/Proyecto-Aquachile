package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.DocumentoCandidato;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentoCandidatoRepository extends JpaRepository<DocumentoCandidato, Long> {
    Optional<DocumentoCandidato> findByCandidato_Id(Long candidatoId);
}
