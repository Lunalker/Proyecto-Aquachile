package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.ExperienciaItemDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.RequisitoMatrizItemDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import com.example.proyectoAquachile.Service.ia.RespuestaIaCrudaDTO.RequisitoIaItemDTO;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Construye y valida determinísticamente la matriz de requisitos del cargo.
 * Garantiza que:
 * 1. La lista de RequisitoCargo en BD sea la única fuente autoritativa.
 * 2. Cada resultado de la IA se asocie únicamente por requisitoId exacto.
 * 3. IDs duplicados, desconocidos u omitidos nunca se asignen arbitrariamente.
 * 4. Ningún requisito quede en ENCONTRADO sin evidencia verificada ni experiencia mínima comprobable.
 */
@Service
public class ConstructorMatrizRequisitosService {

    public static final String ESTADO_ENCONTRADO = "ENCONTRADO";
    public static final String ESTADO_REQUIERE_VERIFICACION = "REQUIERE_VERIFICACION";
    public static final String ESTADO_NO_IDENTIFICADO = "NO_IDENTIFICADO_EN_CV";

    private final VerificadorEvidenciaService verificadorEvidencia;
    private final CalculadoraExperienciaService calculadoraExperiencia;

    public ConstructorMatrizRequisitosService(
            VerificadorEvidenciaService verificadorEvidencia,
            CalculadoraExperienciaService calculadoraExperiencia) {
        this.verificadorEvidencia = verificadorEvidencia;
        this.calculadoraExperiencia = calculadoraExperiencia;
    }

    public List<RequisitoMatrizItemDTO> construirMatriz(
            List<RequisitoCargo> requisitosAutoritativos,
            List<RequisitoIaItemDTO> resultadosIa,
            List<ExperienciaItemDTO> experienciasVerificadas,
            Map<Integer, String> paginasAnonimizadas,
            boolean documentoTruncado) {

        if (requisitosAutoritativos == null || requisitosAutoritativos.isEmpty()) {
            return List.of();
        }

        Map<Long, RequisitoCargo> catalogoPorId = new LinkedHashMap<>();
        for (RequisitoCargo req : requisitosAutoritativos) {
            if (req != null && req.getId() != null) {
                catalogoPorId.put(req.getId(), req);
            }
        }

        Map<Long, RequisitoIaItemDTO> respuestasUnicasPorId = new HashMap<>();
        Set<Long> idsDuplicados = new HashSet<>();

        if (resultadosIa != null) {
            for (RequisitoIaItemDTO item : resultadosIa) {
                if (item == null || item.requisitoId() == null) {
                    continue;
                }
                Long id = item.requisitoId();
                if (!catalogoPorId.containsKey(id)) {
                    continue;
                }
                if (respuestasUnicasPorId.containsKey(id)) {
                    idsDuplicados.add(id);
                } else {
                    respuestasUnicasPorId.put(id, item);
                }
            }
        }

        List<RequisitoMatrizItemDTO> matrizFinal = new ArrayList<>(requisitosAutoritativos.size());

        for (RequisitoCargo req : requisitosAutoritativos) {
            Long id = req.getId();

            if (id != null && idsDuplicados.contains(id)) {
                matrizFinal.add(new RequisitoMatrizItemDTO(
                        id,
                        req.getNombreRequisito(),
                        req.isObligatorio(),
                        ESTADO_REQUIERE_VERIFICACION,
                        "Inconsistencia estructural: el modelo devolvió múltiples evaluaciones para este mismo criterio. Se requiere revisión manual del analista.",
                        new EvidenciaDTO(null, "", "REQUISITO_CARGO", "NO_VERIFICABLE_EN_TEXTO"),
                        "PENDIENTE"
                ));
                continue;
            }

            RequisitoIaItemDTO respuestaIa = id != null ? respuestasUnicasPorId.get(id) : null;

            if (respuestaIa == null) {
                matrizFinal.add(new RequisitoMatrizItemDTO(
                        id,
                        req.getNombreRequisito(),
                        req.isObligatorio(),
                        ESTADO_REQUIERE_VERIFICACION,
                        "El análisis automatizado omitió evaluar este criterio configurado. Requiere verificación directa en el currículum por parte del analista.",
                        new EvidenciaDTO(null, "", "REQUISITO_CARGO", "NO_VERIFICABLE_EN_TEXTO"),
                        "PENDIENTE"
                ));
                continue;
            }

            matrizFinal.add(evaluarYValidarItem(
                    req,
                    respuestaIa,
                    experienciasVerificadas,
                    paginasAnonimizadas,
                    documentoTruncado
            ));
        }

        return matrizFinal;
    }

    private RequisitoMatrizItemDTO evaluarYValidarItem(
            RequisitoCargo req,
            RequisitoIaItemDTO itemIa,
            List<ExperienciaItemDTO> experienciasVerificadas,
            Map<Integer, String> paginasAnonimizadas,
            boolean documentoTruncado) {

        String estadoPropuesto = normalizarEstado(itemIa.resultadoAnalisis());
        String observacion = limpiarObservacion(itemIa.observacionEvidencia());
        EvidenciaDTO evidenciaVerificada = verificarEvidenciaRequisito(
                itemIa.evidencia(),
                req,
                itemIa.condicionesEvaluadas(),
                paginasAnonimizadas
        );
        boolean citaEsValida = VerificadorEvidenciaService.ESTADO_VERIFICADO.equals(evidenciaVerificada.estadoVerificacion());

        if (ESTADO_NO_IDENTIFICADO.equals(estadoPropuesto) && documentoTruncado) {
            return new RequisitoMatrizItemDTO(
                    req.getId(),
                    req.getNombreRequisito(),
                    req.isObligatorio(),
                    ESTADO_REQUIERE_VERIFICACION,
                    observacion + " [Nota del sistema: el documento superó el límite de procesamiento íntegro; verificar manualmente las páginas restantes].",
                    new EvidenciaDTO(null, "", "REQUISITO_CARGO", "NO_VERIFICABLE_EN_TEXTO"),
                    "PENDIENTE"
            );
        }

        if (ESTADO_NO_IDENTIFICADO.equals(estadoPropuesto)) {
            return new RequisitoMatrizItemDTO(
                    req.getId(),
                    req.getNombreRequisito(),
                    req.isObligatorio(),
                    ESTADO_NO_IDENTIFICADO,
                    observacion,
                    new EvidenciaDTO(null, "", "REQUISITO_CARGO", ESTADO_NO_IDENTIFICADO),
                    "PENDIENTE"
            );
        }

        if (ESTADO_ENCONTRADO.equals(estadoPropuesto) && !citaEsValida) {
            return new RequisitoMatrizItemDTO(
                    req.getId(),
                    req.getNombreRequisito(),
                    req.isObligatorio(),
                    ESTADO_REQUIERE_VERIFICACION,
                    observacion + " [Validación backend: la cita textual indicada no pudo verificarse exactamente en el PDF; degradado a verificación humana].",
                    evidenciaVerificada,
                    "PENDIENTE"
            );
        }

        Integer mesesExigidos = req.getExperienciaMinimaMeses();
        if (mesesExigidos != null && mesesExigidos > 0) {
            CalculadoraExperienciaService.ResultadoEvaluacionExperiencia evalExp =
                    calculadoraExperiencia.evaluarCumplimientoMinimo(
                            experienciasVerificadas,
                            req,
                            mesesExigidos,
                            itemIa.experienciasRelacionadasIndices()
                    );

            if (ESTADO_ENCONTRADO.equals(estadoPropuesto) && !evalExp.cumpleMinimoComprobable()) {
                return new RequisitoMatrizItemDTO(
                        req.getId(),
                        req.getNombreRequisito(),
                        req.isObligatorio(),
                        ESTADO_REQUIERE_VERIFICACION,
                        observacion + " [Cálculo backend: " + evalExp.detalleCalculo() + "].",
                        evidenciaVerificada,
                        "PENDIENTE"
                );
            }

            if (evalExp.detalleCalculo() != null && !evalExp.detalleCalculo().isBlank()) {
                observacion = observacion + " [Cálculo backend: " + evalExp.detalleCalculo() + "].";
            }
        }

        return new RequisitoMatrizItemDTO(
                req.getId(),
                req.getNombreRequisito(),
                req.isObligatorio(),
                estadoPropuesto,
                observacion,
                evidenciaVerificada,
                "PENDIENTE"
        );
    }

    private EvidenciaDTO verificarEvidenciaRequisito(
            EvidenciaDTO evidenciaIa,
            RequisitoCargo req,
            List<String> condicionesEvaluadas,
            Map<Integer, String> paginasAnonimizadas) {
        if (evidenciaIa == null || evidenciaIa.fragmento() == null || evidenciaIa.fragmento().isBlank()) {
            return new EvidenciaDTO(null, "", "REQUISITO_CARGO", "NO_VERIFICABLE_EN_TEXTO");
        }
        StringBuilder contexto = new StringBuilder();
        if (req.getNombreRequisito() != null) contexto.append(req.getNombreRequisito()).append(' ');
        if (req.getDescripcion() != null) contexto.append(req.getDescripcion()).append(' ');
        if (condicionesEvaluadas != null && !condicionesEvaluadas.isEmpty()) {
            contexto.append(String.join(" ", condicionesEvaluadas));
        }
        return verificadorEvidencia.verificarEvidenciaContextual(
                evidenciaIa,
                "REQUISITO_CARGO",
                contexto.toString().trim(),
                paginasAnonimizadas
        );
    }

    private String normalizarEstado(String raw) {
        if (raw == null || raw.isBlank()) {
            return ESTADO_REQUIERE_VERIFICACION;
        }
        String limpio = raw.trim().toUpperCase(Locale.ROOT);
        return switch (limpio) {
            case ESTADO_ENCONTRADO -> ESTADO_ENCONTRADO;
            case ESTADO_NO_IDENTIFICADO -> ESTADO_NO_IDENTIFICADO;
            case ESTADO_REQUIERE_VERIFICACION -> ESTADO_REQUIERE_VERIFICACION;
            default -> ESTADO_REQUIERE_VERIFICACION;
        };
    }

    private String limpiarObservacion(String obs) {
        if (obs == null || obs.isBlank()) {
            return "Sin observación detallada proporcionada por el análisis.";
        }
        return obs.trim();
    }
}