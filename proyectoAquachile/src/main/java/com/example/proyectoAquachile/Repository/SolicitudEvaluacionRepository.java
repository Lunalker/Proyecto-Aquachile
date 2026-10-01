package com.example.proyectoAquachile.Repository; // Ajusta a tu paquete real

import com.example.proyectoAquachile.Model.SolicitudEvaluacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SolicitudEvaluacionRepository extends JpaRepository<SolicitudEvaluacion, Long> {
}