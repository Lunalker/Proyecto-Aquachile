package com.example.proyectoAquachile.Dto;

import java.time.LocalDateTime;

/**
 * Respuesta enriquecida del análisis documental hacia el frontend, separando metadatos de control,
 * estado de revisión humana, antecedentes originales de IA y antecedentes revisados/validados.
 */
public record AnalisisCvResponseDTO(
        Long analisisId,
        Long candidatoId,
        String nombreCandidatoInterno,
        String cargoPostula,
        String familiaCargo,
        String nombreArchivoAnalizado,
        String hashDocumento,
        String modeloUtilizado,
        LocalDateTime fechaGeneracion,
        String estadoAnalisis,
        String estadoRevision,
        String usuarioValidador,
        LocalDateTime fechaValidacion,
        String observacionesAnalista,
        AnalisisCvDTO resultadoIaOriginal,
        AnalisisCvDTO datosActuales,
        PreguntasApoyoDTO preguntasApoyo
) {}