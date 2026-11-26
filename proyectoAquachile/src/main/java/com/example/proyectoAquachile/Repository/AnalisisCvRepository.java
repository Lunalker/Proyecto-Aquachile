package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.AnalisisCv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalisisCvRepository extends JpaRepository<AnalisisCv, Long> {

    Optional<AnalisisCv> findFirstByPostulacionIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
            Long postulacionId, String estadoAnalisis);

    List<AnalisisCv> findByPostulacionIdAndEstadoAnalisis(Long postulacionId, String estadoAnalisis);

    List<AnalisisCv> findByPostulacionIdOrderByFechaGeneracionDesc(Long postulacionId);

    Optional<AnalisisCv> findFirstByCandidatoIdAndEstadoAnalisisOrderByFechaGeneracionDesc(
            Long candidatoId, String estadoAnalisis);

    List<AnalisisCv> findByCandidatoIdAndEstadoAnalisis(Long candidatoId, String estadoAnalisis);

    List<AnalisisCv> findByCandidatoIdOrderByFechaGeneracionDesc(Long candidatoId);
}