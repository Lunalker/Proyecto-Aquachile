package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.Postulacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostulacionRepository extends JpaRepository<Postulacion, Long> {
    boolean existsByCandidato_IdAndOfertaLaboral_Id(Long candidatoId, Long ofertaLaboralId);
    Optional<Postulacion> findByCandidato_IdAndOfertaLaboral_Id(Long candidatoId, Long ofertaLaboralId);
    List<Postulacion> findByCandidato_IdOrderByFechaPostulacionDesc(Long candidatoId);
    List<Postulacion> findAllByOrderByFechaPostulacionDesc();
    Optional<Postulacion> findByContenedorUuid(String contenedorUuid);
}