package com.example.proyectoAquachile.Dto;

import java.util.List;

public record AnalisisCvDTO(
        String nombreDetectado,
        String tituloProfesional,
        Integer anosExperienciaEstimados,
        List<String> habilidadesClave,
        List<String> certificacionesYLicencias,
        String ultimoCargo,
        String resumenEjecutivo,
        String observacionesReclutador
) {}