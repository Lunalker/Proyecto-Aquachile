package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO;
import com.example.proyectoAquachile.Dto.AnalisisCvDTO.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Enmascara información personal identificable (PII) antes de enviar datos al proveedor LLM,
 * preservando información profesional como empresas, cargos e instituciones educativas.
 */
@Service
public class AnonimizadorPiiService {

    private static final Pattern PATRON_RUT = Pattern.compile(
            "\\b\\d{1,2}(?:\\.\\d{3}){2}-[0-9kK]\\b|\\b\\d{7,8}-[0-9kK]\\b"
    );
    private static final Pattern PATRON_EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );
    private static final Pattern PATRON_TELEFONO = Pattern.compile(
            "(?:\\+?56\\s*)?(?:0?9\\s*\\d{4}[\\s-]?\\d{4}|\\(?0?\\d{1,2}\\)?[\\s-]?\\d{6,7})\\b"
    );
    private static final Pattern PATRON_DIRECCION = Pattern.compile(
            "(?i)\\b(?:direcci[oó]n|domicilio|residencia)\\s*[:\\-]?\\s*[^\\n\\r.;|]{5,80}"
    );
    private static final Pattern PATRON_CALLE = Pattern.compile(
            "(?i)\\b(?:av\\.?|avenida|calle|pasaje|pje\\.?|villa|poblaci[oó]n)\\s+[\\p{L}0-9\\s.,#-]{4,55}\\d+"
    );

    public String anonimizarTextoCv(String texto, String nombreCandidato) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String resultado = anonimizarTextoLibre(texto);

        if (nombreCandidato != null && !nombreCandidato.isBlank()) {
            String completo = nombreCandidato.trim();
            resultado = resultado.replaceAll("(?i)" + Pattern.quote(completo), "[CANDIDATO_ANONIMIZADO]");
            for (String parte : completo.split("\\s+")) {
                if (parte.length() >= 4) {
                    resultado = resultado.replaceAll("(?i)\\b" + Pattern.quote(parte) + "\\b", "[NOMBRE_OCULTO]");
                }
            }
        }
        return resultado;
    }

    public String anonimizarObservaciones(String observaciones) {
        return anonimizarTextoLibre(observaciones);
    }

    public String anonimizarTextoLibre(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String limpio = PATRON_RUT.matcher(texto).replaceAll("[RUT_OCULTO]");
        limpio = PATRON_EMAIL.matcher(limpio).replaceAll("[EMAIL_OCULTO]");
        limpio = PATRON_TELEFONO.matcher(limpio).replaceAll("[TELEFONO_OCULTO]");
        limpio = PATRON_DIRECCION.matcher(limpio).replaceAll("[DIRECCION_OCULTA]");
        limpio = PATRON_CALLE.matcher(limpio).replaceAll("[DIRECCION_OCULTA]");
        return limpio;
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
                "[CANDIDATO_ANONIMIZADO]",
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