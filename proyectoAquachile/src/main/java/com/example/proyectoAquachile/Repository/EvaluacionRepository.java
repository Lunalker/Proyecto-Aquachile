package com.example.proyectoAquachile.Repository; // Ajusta a tu paquete real

import com.example.proyectoAquachile.Model.Evaluacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {
}