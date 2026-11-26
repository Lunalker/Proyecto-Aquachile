package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.OfertaLaboral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfertaLaboralRepository extends JpaRepository<OfertaLaboral, Long> {
    List<OfertaLaboral> findByEstadoPublicacionOrderByFechaPublicacionDesc(String estadoPublicacion);
    Optional<OfertaLaboral> findByIdAndEstadoPublicacion(Long id, String estadoPublicacion);
}