package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.ExperienciaItemDTO;
import com.example.proyectoAquachile.Model.RequisitoCargo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calcula duraciones laborales de forma determinística en Java.
 * - Utiliza Clock controlado en zona America/Santiago.
 * - Fusiona periodos simultáneos o superpuestos para no duplicar experiencia cronológica.
 * - No inventa meses cuando el CV solo indica años (ej. "2022 - 2023").
 */
@Service
public class CalculadoraExperienciaService {

    public static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("ene", 1), Map.entry("enero", 1), Map.entry("jan", 1),
            Map.entry("feb", 2), Map.entry("febrero", 2),
            Map.entry("mar", 3), Map.entry("marzo", 3),
            Map.entry("abr", 4), Map.entry("abril", 4), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("mayo", 5),
            Map.entry("jun", 6), Map.entry("junio", 6),
            Map.entry("jul", 7), Map.entry("julio", 7),
            Map.entry("ago", 8), Map.entry("agosto", 8), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("sept", 9), Map.entry("septiembre", 9),
            Map.entry("oct", 10), Map.entry("octubre", 10),
            Map.entry("nov", 11), Map.entry("noviembre", 11),
            Map.entry("dic", 12), Map.entry("diciembre", 12), Map.entry("dec", 12)
    );

    private static final Pattern PATRON_MES_ANIO = Pattern.compile(
            "(?:(ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|jul(?:io)?|ago(?:sto)?|sep(?:t|tiembre)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?|jan|apr|aug|dec)[./\\s-]*(20\\d{2}|19\\d{2})|(0?[1-9]|1[0-2])[/-](20\\d{2}|19\\d{2})|(20\\d{2}|19\\d{2})[/-](0?[1-9]|1[0-2]))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATRON_SOLO_ANIO = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern PATRON_VIGENTE = Pattern.compile(
            "\\b(actualidad|actual|presente|a la fecha|vigente|hoy|current|present)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Clock clock;

    @Autowired
    public CalculadoraExperienciaService() {
        this(Clock.system(ZONA_CHILE));
    }

    public CalculadoraExperienciaService(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(ZONA_CHILE);
    }

    public record IntervaloParsed(
            YearMonth inicio,
            YearMonth fin,
            boolean exacto,
            boolean vigente,
            boolean inconsistente,
            String motivoInconsistencia
    ) {
        public int mesesInclusivos() {
            if (!exacto || inconsistente || inicio == null || fin == null) return 0;
            return (int) ChronoUnit.MONTHS.between(inicio, fin) + 1;
        }
    }

    public record ResultadoEvaluacionExperiencia(
            boolean cumpleMinimoComprobable,
            int mesesComprobables,
            boolean tieneFechasIncompletas,
            boolean tieneSolapamientos,
            String detalleCalculo
    ) {}

    public record ResumenCronologicoGlobal(
            int mesesCronologicosTotales,
            boolean tieneSolapamientos,
            String notaSolapamiento
    ) {}

    public String calcularDuracionIndividual(String periodoTexto, String sugerenciaIa) {
        IntervaloParsed parsed = parsearPeriodo(periodoTexto);
        if (parsed.inconsistente()) {
            return "Periodo inconsistente: " + parsed.motivoInconsistencia();
        }
        if (!parsed.exacto()) {
            if (parsed.inicio() != null && parsed.fin() != null) {
                return "Periodo expresado solo en años (" + periodoTexto + "); requiere confirmación de meses exactos";
            }
            return "No calculable por falta de fechas explícitas";
        }
        int meses = parsed.mesesInclusivos();
        String base = formatearMesesLegible(meses);
        return parsed.vigente() ? base + " (vigente a la fecha)" : base;
    }

    public IntervaloParsed parsearPeriodo(String periodoRaw) {
        if (periodoRaw == null || periodoRaw.isBlank()
                || periodoRaw.equalsIgnoreCase("NO_IDENTIFICADO_EN_CV")) {
            return new IntervaloParsed(null, null, false, false, false, "Sin fechas");
        }

        String norm = normalizar(periodoRaw);
        boolean vigente = PATRON_VIGENTE.matcher(norm).find();
        YearMonth hoy = YearMonth.now(clock.withZone(ZONA_CHILE));

        Matcher mMesAnio = PATRON_MES_ANIO.matcher(norm);
        List<YearMonth> fechasExactas = new ArrayList<>();
        while (mMesAnio.find()) {
            YearMonth ym = extraerYearMonth(mMesAnio);
            if (ym != null) {
                fechasExactas.add(ym);
            }
        }

        if (fechasExactas.size() >= 2) {
            YearMonth ini = fechasExactas.get(0);
            YearMonth fin = fechasExactas.get(1);
            if (fin.isBefore(ini)) {
                return new IntervaloParsed(ini, fin, false, false, true,
                        "la fecha de término (" + fin + ") es anterior al inicio (" + ini + ")");
            }
            return new IntervaloParsed(ini, fin, true, false, false, null);
        }

        if (fechasExactas.size() == 1) {
            YearMonth ini = fechasExactas.get(0);
            if (vigente) {
                if (hoy.isBefore(ini)) {
                    return new IntervaloParsed(ini, hoy, false, true, true,
                            "la fecha de inicio (" + ini + ") es posterior a la fecha actual");
                }
                return new IntervaloParsed(ini, hoy, true, true, false, null);
            }
            // Fecha de inicio con mes/año pero sin término explícito ni indicador de vigencia
            return new IntervaloParsed(ini, null, false, false, false, "Falta fecha de término");
        }

        // Revisar si solo vienen años (ej. "2022 - 2023" o "2023 - Actualidad")
        Matcher mAnios = PATRON_SOLO_ANIO.matcher(norm);
        List<Integer> anios = new ArrayList<>();
        while (mAnios.find()) {
            anios.add(Integer.parseInt(mAnios.group(1)));
        }

        if (anios.size() >= 2) {
            int a1 = anios.get(0);
            int a2 = anios.get(1);
            if (a2 < a1) {
                return new IntervaloParsed(YearMonth.of(a1, 1), YearMonth.of(a2, 12), false, false, true,
                        "el año de término (" + a2 + ") es anterior al de inicio (" + a1 + ")");
            }
            // No inventa meses exactos para rangos expresados solo en años
            return new IntervaloParsed(YearMonth.of(a1, 1), YearMonth.of(a2, 12), false, false, false,
                    "Solo se indican años sin mes de inicio ni término");
        }

        if (anios.size() == 1 && vigente) {
            int a1 = anios.get(0);
            if (hoy.getYear() < a1) {
                return new IntervaloParsed(YearMonth.of(a1, 1), hoy, false, true, true,
                        "el año de inicio (" + a1 + ") es posterior al año actual");
            }
            return new IntervaloParsed(YearMonth.of(a1, 1), hoy, false, true, false,
                    "Solo se indica año de inicio sin mes exacto");
        }

        return new IntervaloParsed(null, null, false, vigente, false, "Formato de fecha no exacto");
    }

    public ResumenCronologicoGlobal calcularExperienciaCronologicaGlobal(List<ExperienciaItemDTO> experiencias) {
        if (experiencias == null || experiencias.isEmpty()) {
            return new ResumenCronologicoGlobal(0, false, "");
        }

        List<IntervaloParsed> intervalosExactos = new ArrayList<>();
        Set<String> huellasDeduplicacion = new HashSet<>();

        for (ExperienciaItemDTO exp : experiencias) {
            if (exp == null) continue;
            String huella = normalizar(exp.cargo() + "|" + exp.empresa() + "|" + exp.periodo());
            if (!huellasDeduplicacion.add(huella)) {
                continue; // Experiencia repetida ignorada
            }
            IntervaloParsed parsed = parsearPeriodo(exp.periodo());
            if (parsed.exacto() && !parsed.inconsistente()) {
                intervalosExactos.add(parsed);
            }
        }

        if (intervalosExactos.isEmpty()) {
            return new ResumenCronologicoGlobal(0, false, "");
        }

        int sumaBruta = intervalosExactos.stream().mapToInt(IntervaloParsed::mesesInclusivos).sum();
        List<IntervaloParsed> fusionados = fusionarIntervalos(intervalosExactos);
        int mesesCronologicos = fusionados.stream().mapToInt(IntervaloParsed::mesesInclusivos).sum();

        boolean haySolapamiento = sumaBruta > mesesCronologicos;
        String nota = haySolapamiento
                ? "Se detectaron experiencias simultáneas o superpuestas (suma bruta: "
                  + formatearMesesLegible(sumaBruta) + "; tiempo cronológico real sin duplicar: "
                  + formatearMesesLegible(mesesCronologicos) + ")."
                : "";

        return new ResumenCronologicoGlobal(mesesCronologicos, haySolapamiento, nota);
    }

    public ResultadoEvaluacionExperiencia evaluarCumplimientoMinimo(
            List<ExperienciaItemDTO> experiencias,
            RequisitoCargo requisito,
            int mesesMinimosExigidos) {

        if (experiencias == null || experiencias.isEmpty()) {
            return new ResultadoEvaluacionExperiencia(
                    false, 0, false, false,
                    "No existen experiencias laborales documentadas para acreditar " + formatearMesesLegible(mesesMinimosExigidos)
            );
        }

        List<ExperienciaItemDTO> relevantes = filtrarExperienciasRelevantes(experiencias, requisito);
        if (relevantes.isEmpty()) {
            return new ResultadoEvaluacionExperiencia(
                    false, 0, false, false,
                    "No se identificaron experiencias directamente afines al criterio para validar " + formatearMesesLegible(mesesMinimosExigidos)
            );
        }

        List<IntervaloParsed> exactos = new ArrayList<>();
        Set<String> huellas = new HashSet<>();
        boolean hayIncompletas = false;
        boolean hayInconsistentes = false;
        boolean incluyePractica = false;

        for (ExperienciaItemDTO exp : relevantes) {
            String huella = normalizar(exp.cargo() + "|" + exp.empresa() + "|" + exp.periodo());
            if (!huellas.add(huella)) {
                continue; // Evita sumar experiencias repetidas
            }
            if (esPracticaProfesional(exp)) {
                incluyePractica = true;
            }
            IntervaloParsed p = parsearPeriodo(exp.periodo());
            if (p.inconsistente()) {
                hayInconsistentes = true;
            } else if (p.exacto()) {
                exactos.add(p);
            } else {
                hayIncompletas = true;
            }
        }

        int sumaBruta = exactos.stream().mapToInt(IntervaloParsed::mesesInclusivos).sum();
        List<IntervaloParsed> fusionados = fusionarIntervalos(exactos);
        int mesesReales = fusionados.stream().mapToInt(IntervaloParsed::mesesInclusivos).sum();
        boolean haySolapamiento = sumaBruta > mesesReales;

        boolean cumple = !hayInconsistentes && mesesReales >= mesesMinimosExigidos;

        StringBuilder detalle = new StringBuilder();
        detalle.append("Experiencia cronológica comprobable: ").append(formatearMesesLegible(mesesReales))
                .append(" frente a un mínimo de ").append(formatearMesesLegible(mesesMinimosExigidos));

        if (haySolapamiento) {
            detalle.append(" (periodos simultáneos fusionados sin doble conteo)");
        }
        if (hayIncompletas) {
            detalle.append("; existen periodos con fechas incompletas (solo años o sin término)");
        }
        if (hayInconsistentes) {
            detalle.append("; se detectó un periodo con fechas inconsistentes");
        }
        if (incluyePractica) {
            detalle.append("; incluye periodo de práctica profesional");
        }

        return new ResultadoEvaluacionExperiencia(
                cumple,
                mesesReales,
                hayIncompletas,
                haySolapamiento,
                detalle.toString()
        );
    }

    private List<ExperienciaItemDTO> filtrarExperienciasRelevantes(
            List<ExperienciaItemDTO> experiencias,
            RequisitoCargo req) {

        Set<String> keywordsReq = extraerKeywords(
                (req.getNombreRequisito() != null ? req.getNombreRequisito() : "") + " " +
                        (req.getDescripcion() != null ? req.getDescripcion() : "")
        );

        // Si el criterio es genérico de experiencia sin términos técnicos específicos, considerar todas las experiencias
        if (keywordsReq.isEmpty()) {
            return experiencias;
        }

        List<ExperienciaItemDTO> afines = new ArrayList<>();
        for (ExperienciaItemDTO exp : experiencias) {
            if (exp == null) continue;
            String textoExp = normalizar(
                    (exp.cargo() != null ? exp.cargo() : "") + " " +
                            (exp.empresa() != null ? exp.empresa() : "") + " " +
                            (exp.funciones() != null ? String.join(" ", exp.funciones()) : "")
            );
            boolean coincide = keywordsReq.stream().anyMatch(textoExp::contains);
            if (coincide) {
                afines.add(exp);
            }
        }
        return afines;
    }

    private boolean esPracticaProfesional(ExperienciaItemDTO exp) {
        String cargoNorm = normalizar(exp.cargo());
        return cargoNorm.contains("practica") || cargoNorm.contains("practicante") || cargoNorm.contains("pasantia");
    }

    private List<IntervaloParsed> fusionarIntervalos(List<IntervaloParsed> lista) {
        if (lista.size() <= 1) return lista;
        List<IntervaloParsed> ordenados = new ArrayList<>(lista);
        ordenados.sort(Comparator.comparing(IntervaloParsed::inicio));

        List<IntervaloParsed> resultado = new ArrayList<>();
        IntervaloParsed actual = ordenados.get(0);

        for (int i = 1; i < ordenados.size(); i++) {
            IntervaloParsed siguiente = ordenados.get(i);
            // Si siguiente empieza antes o justo el mes siguiente al término de actual, se fusionan
            if (!siguiente.inicio().isAfter(actual.fin().plusMonths(1))) {
                YearMonth nuevoFin = siguiente.fin().isAfter(actual.fin()) ? siguiente.fin() : actual.fin();
                actual = new IntervaloParsed(
                        actual.inicio(),
                        nuevoFin,
                        true,
                        actual.vigente() || siguiente.vigente(),
                        false,
                        null
                );
            } else {
                resultado.add(actual);
                actual = siguiente;
            }
        }
        resultado.add(actual);
        return resultado;
    }

    private YearMonth extraerYearMonth(Matcher m) {
        if (m.group(1) != null && m.group(2) != null) {
            Integer mes = MESES.get(m.group(1).toLowerCase(Locale.ROOT));
            int anio = Integer.parseInt(m.group(2));
            return mes != null ? YearMonth.of(anio, mes) : null;
        }
        if (m.group(3) != null && m.group(4) != null) {
            int mes = Integer.parseInt(m.group(3));
            int anio = Integer.parseInt(m.group(4));
            return YearMonth.of(anio, mes);
        }
        if (m.group(5) != null && m.group(6) != null) {
            int anio = Integer.parseInt(m.group(5));
            int mes = Integer.parseInt(m.group(6));
            return YearMonth.of(anio, mes);
        }
        return null;
    }

    private Set<String> extraerKeywords(String texto) {
        Set<String> stopwords = Set.of(
                "experiencia", "laboral", "previa", "minima", "minimo", "anos", "meses",
                "cargo", "area", "funciones", "equivalentes", "acorde", "perfil", "trabajo",
                "conocimiento", "manejo", "uso", "nivel", "para", "con", "sin", "los", "las", "del"
        );
        Set<String> out = new HashSet<>();
        for (String tok : normalizar(texto).split("\\s+")) {
            if (tok.length() >= 4 && !stopwords.contains(tok)) {
                out.add(tok);
            }
        }
        return out;
    }

    private String formatearMesesLegible(int totalMeses) {
        if (totalMeses <= 0) return "0 meses";
        int anos = totalMeses / 12;
        int meses = totalMeses % 12;
        if (anos == 0) return meses + (meses == 1 ? " mes" : " meses");
        if (meses == 0) return anos + (anos == 1 ? " año" : " años");
        return anos + (anos == 1 ? " año y " : " años y ") + meses + (meses == 1 ? " mes" : " meses");
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}