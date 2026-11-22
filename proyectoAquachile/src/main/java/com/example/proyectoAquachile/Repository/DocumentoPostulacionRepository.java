package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.DocumentoPostulacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentoPostulacionRepository extends JpaRepository<DocumentoPostulacion, Long> {
    Optional<DocumentoPostulacion> findByPostulacion_Id(Long postulacionId);
}