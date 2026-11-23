package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.*;
import com.example.proyectoAquachile.Service.AnonimizadorCvUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio Spring de anonimización PII antes de enviar datos al proveedor LLM,
 * delegando las reglas de sanitización en {@link AnonimizadorCvUtil}.
 */
@Service
public class AnonimizadorPiiService {

    public String anonimizarTextoCv(String texto, String nombreCandidato) {
        return AnonimizadorCvUtil.anonimizarTexto(texto, nombreCandidato);
    }

    public String anonimizarObservaciones(String observaciones) {
        return AnonimizadorCvUtil.anonimizarObservaciones(observaciones);
    }

    public String anonimizarTextoLibre(String texto) {
        return AnonimizadorCvUtil.anonimizarTexto(texto, null);
    }

    public AnalisisCvDTO anonimizarAnalisisDto(AnalisisCvDTO dto) {
        if (dto == null) return null;

        List<FormacionItemDTO> formacion = dto.formacionAcademica() == null ? List.of() :
                dto.formacionAcademica().stream().map(f -> new FormacionItemDTO(
                        anonimizarTextoLibre(f.titulo()),
                        anonimizarTextoLibre(f.institucion()),
                        anonimizarTextoLibre(f.periodo()),
                        anonimizarTextoLibre(f.estadoAcademico()),
                        anonimizarEvidencia(f.evidencia()),
                        f.estadoRevisionItem()
                )).toList();

        List<ExperienciaItemDTO> experiencias = dto.experienciasLaborales() == null ? List.of() :
                dto.experienciasLaborales().stream().map(e -> new ExperienciaItemDTO(
                        anonimizarTextoLibre(e.cargo()),
                        anonimizarTextoLibre(e.empresa()),
                        anonimizarTextoLibre(e.periodo()),
                        anonimizarTextoLibre(e.duracionCalculada()),
                        e.funciones() == null ? List.of() : e.funciones().stream().map(this::anonimizarTextoLibre).toList(),
                        anonimizarEvidencia(e.evidencia()),
                        e.estadoRevisionItem()
                )).toList();

        List<HabilidadItemDTO> habilidades = dto.habilidadesTecnicas() == null ? List.of() :
                dto.habilidadesTecnicas().stream().map(h -> new HabilidadItemDTO(
                        anonimizarTextoLibre(h.nombre()),
                        anonimizarTextoLibre(h.contextoUso()),
                        anonimizarEvidencia(h.evidencia()),
                        h.estadoRevisionItem()
                )).toList();

        List<CompetenciaItemDTO> competencias = dto.competenciasDeclaradas() == null ? List.of() :
                dto.competenciasDeclaradas().stream().map(c -> new CompetenciaItemDTO(
                        anonimizarTextoLibre(c.nombre()),
                        anonimizarEvidencia(c.evidencia()),
                        c.estadoRevisionItem()
                )).toList();

        List<CertificacionItemDTO> certificaciones = dto.certificacionesYLicencias() == null ? List.of() :
                dto.certificacionesYLicencias().stream().map(c -> new CertificacionItemDTO(
                        anonimizarTextoLibre(c.nombre()),
                        anonimizarTextoLibre(c.entidadEmisora()),
                        anonimizarTextoLibre(c.vigencia()),
                        anonimizarEvidencia(c.evidencia()),
                        c.estadoRevisionItem()
                )).toList();

        List<RequisitoMatrizItemDTO> matriz = dto.matrizRequisitos() == null ? List.of() :
                dto.matrizRequisitos().stream().map(m -> new RequisitoMatrizItemDTO(
                        m.requisitoId(),
                        anonimizarTextoLibre(m.requisito()),
                        m.obligatorio(),
                        m.resultadoAnalisis(),
                        anonimizarTextoLibre(m.observacionEvidencia()),
                        anonimizarEvidencia(m.evidencia()),
                        m.estadoRevisionItem()
                )).toList();

        List<DatoFaltanteItemDTO> faltantes = dto.datosFaltantesOAmbiguos() == null ? List.of() :
                dto.datosFaltantesOAmbiguos().stream().map(d -> new DatoFaltanteItemDTO(
                        anonimizarTextoLibre(d.antecedente()),
                        anonimizarTextoLibre(d.motivo()),
                        d.requiereRevisionHumana()
                )).toList();

        return new AnalisisCvDTO(
                "[CANDIDATO]",
                anonimizarTextoLibre(dto.tituloProfesional()),
                anonimizarTextoLibre(dto.resumenProfesionalObjetivo()),
                formacion,
                experiencias,
                habilidades,
                competencias,
                certificaciones,
                matriz,
                faltantes
        );
    }

    private EvidenciaDTO anonimizarEvidencia(EvidenciaDTO ev) {
        if (ev == null) return null;
        return new EvidenciaDTO(
                ev.pagina(),
                anonimizarTextoLibre(ev.fragmento()),
                ev.tipoAntecedente(),
                ev.estadoVerificacion()
        );
    }
}