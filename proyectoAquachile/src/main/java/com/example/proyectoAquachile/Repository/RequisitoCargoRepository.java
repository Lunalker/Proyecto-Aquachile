package com.example.proyectoAquachile.Repository;

import com.example.proyectoAquachile.Model.RequisitoCargo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequisitoCargoRepository extends JpaRepository<RequisitoCargo, Long> {

    List<RequisitoCargo> findByCargoIgnoreCaseAndActivoTrueOrderByIdAsc(String cargo);

    List<RequisitoCargo> findByActivoTrueOrderByCargoAscIdAsc();
}