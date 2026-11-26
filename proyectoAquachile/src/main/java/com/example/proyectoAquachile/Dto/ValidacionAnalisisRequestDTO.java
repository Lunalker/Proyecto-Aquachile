package com.example.proyectoAquachile.Dto;

/**
 * Solicitud enviada por el ANALISTA para guardar correcciones o validar los antecedentes extraídos.
 */
public record ValidacionAnalisisRequestDTO(
        AnalisisCvDTO datosRevisados,
        String observacionesAnalista,
        boolean confirmarValidacion
) {}