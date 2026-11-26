package com.example.proyectoAquachile.Dto;

import java.time.LocalDateTime;

public record AnalisisCvResponseDTO(
        Long analisisId,
        Long postulacionId,
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
) {
    public AnalisisCvResponseDTO(
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
    ) {
        this(
                analisisId, null, candidatoId, nombreCandidatoInterno, cargoPostula, familiaCargo,
                nombreArchivoAnalizado, hashDocumento, modeloUtilizado, fechaGeneracion,
                estadoAnalisis, estadoRevision, usuarioValidador, fechaValidacion,
                observacionesAnalista, resultadoIaOriginal, datosActuales, preguntasApoyo
        );
    }
}