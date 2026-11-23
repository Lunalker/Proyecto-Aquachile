package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.*;
import java.util.List;

/**
 * Representación estructurada devuelta directamente por el LLM bajo JSON Schema estricto.
 * Incluye trazabilidad hacia los índices de experiencias laborales relevantes para que Java
 * calcule determinísticamente la duración cronológica sin duplicar periodos simultáneos.
 */
public record RespuestaIaCrudaDTO(
        String nombreDetectado,
        String tituloProfesional,
        String resumenProfesionalObjetivo,
        List<FormacionItemDTO> formacionAcademica,
        List<ExperienciaItemDTO> experienciasLaborales,
        List<HabilidadItemDTO> habilidadesTecnicas,
        List<CompetenciaItemDTO> competenciasDeclaradas,
        List<CertificacionItemDTO> certificacionesYLicencias,
        List<RequisitoIaItemDTO> matrizRequisitos,
        List<DatoFaltanteItemDTO> datosFaltantesOAmbiguos
) {
    public record RequisitoIaItemDTO(
            Long requisitoId,
            String requisito,
            boolean obligatorio,
            String resultadoAnalisis,
            List<String> condicionesEvaluadas,
            List<Integer> experienciasRelacionadasIndices,
            String observacionEvidencia,
            EvidenciaDTO evidencia
    ) {}
}