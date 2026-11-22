package com.example.proyectoAquachile.Dto;

import java.util.List;

/**
 * Estructura profesional del análisis documental asistido por IA con evidencias verificables,
 * matriz de requisitos del cargo y estados de revisión humana por antecedente.
 */
public record AnalisisCvDTO(
        String nombreDetectado,
        String tituloProfesional,
        String resumenProfesionalObjetivo,
        List<FormacionItemDTO> formacionAcademica,
        List<ExperienciaItemDTO> experienciasLaborales,
        List<HabilidadItemDTO> habilidadesTecnicas,
        List<CompetenciaItemDTO> competenciasDeclaradas,
        List<CertificacionItemDTO> certificacionesYLicencias,
        List<RequisitoMatrizItemDTO> matrizRequisitos,
        List<DatoFaltanteItemDTO> datosFaltantesOAmbiguos
) {
    public record EvidenciaDTO(
            Integer pagina,
            String fragmento,
            String tipoAntecedente,
            String estadoVerificacion
    ) {}

    public record FormacionItemDTO(
            String titulo,
            String institucion,
            String periodo,
            String estadoAcademico,
            EvidenciaDTO evidencia,
            String estadoRevisionItem
    ) {}

    public record ExperienciaItemDTO(
            String cargo,
            String empresa,
            String periodo,
            String duracionCalculada,
            List<String> funciones,
            EvidenciaDTO evidencia,
            String estadoRevisionItem
    ) {}

    public record HabilidadItemDTO(
            String nombre,
            String contextoUso,
            EvidenciaDTO evidencia,
            String estadoRevisionItem
    ) {}

    public record CompetenciaItemDTO(
            String nombre,
            EvidenciaDTO evidencia,
            String estadoRevisionItem
    ) {}

    public record CertificacionItemDTO(
            String nombre,
            String entidadEmisora,
            String vigencia,
            EvidenciaDTO evidencia,
            String estadoRevisionItem
    ) {}

    public record RequisitoMatrizItemDTO(
            Long requisitoId,
            String requisito,
            boolean obligatorio,
            String resultadoAnalisis,
            String observacionEvidencia,
            EvidenciaDTO evidencia,
            String estadoRevisionItem
    ) {}

    public record DatoFaltanteItemDTO(
            String antecedente,
            String motivo,
            boolean requiereRevisionHumana
    ) {}
}