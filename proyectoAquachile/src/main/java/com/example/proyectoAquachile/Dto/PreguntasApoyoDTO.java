package com.example.proyectoAquachile.Dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Preguntas sugeridas para apoyar la entrevista o evaluación profesional del EVALUADOR,
 * derivadas de brechas, ambigüedades o requisitos pendientes de verificación.
 */
public record PreguntasApoyoDTO(
        LocalDateTime fechaGeneracion,
        String modeloUtilizado,
        List<PreguntaSugeridaItem> preguntas
) {
    public record PreguntaSugeridaItem(
            String pregunta,
            String objetivoVerificacion,
            String antecedenteRelacionado
    ) {}
}