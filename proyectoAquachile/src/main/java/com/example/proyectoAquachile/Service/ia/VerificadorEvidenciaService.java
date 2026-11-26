package com.example.proyectoAquachile.Service.ia;

import com.example.proyectoAquachile.Dto.AnalisisCvDTO.EvidenciaDTO;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verificador programático estricto de evidencias documentales.
 * Exige coincidencia textual contigua (con normalización controlada de formato PDF),
 * registra posiciones exactas de inicio/fin en el texto extraído, rechaza palabras sueltas
 * dispersas y degrada obligatoriamente cualquier estado ENCONTRADO cuya evidencia no sea verificable.
 */
@Service
public class VerificadorEvidenciaService {

    public static final String ESTADO_VERIFICADO = "VERIFICADO_EN_DOCUMENTO";
    public static final String ESTADO_NO_VERIFICABLE = "NO_VERIFICABLE_EN_TEXTO";
    public static final String ESTADO_AMBIGUO_PAGINAS = "AMBIGUA_MULTIPLES_PAGINAS";
    public static final String ESTADO_SIN_CONTEXTO = "SIN_RESPALDO_CONTEXTUAL";
    public static final String ESTADO_NO_IDENTIFICADO = "NO_IDENTIFICADO_EN_CV";

    public record ResultadoVerificacionEvidencia(
            EvidenciaDTO evidencia,
            boolean verificada,
            Integer inicioCaracter,
            Integer finCaracter,
            String motivoDetalle
    ) {}

    private record CoincidenciaPagina(int pagina, int[] rango) {}

    private record TextoMapeado(
            String normalizado,
            int[] mapaIndicesOriginal
    ) {}

    public VerificadorEvidenciaService() {
    }

    /**
     * Método usado por ConstructorMatrizRequisitosService y CvAnalisisService.
     * Devuelve el EvidenciaDTO normalizado con estado VERIFICADO_EN_DOCUMENTO o NO_VERIFICABLE_EN_TEXTO.
     */
    public EvidenciaDTO verificarEvidenciaContextual(
            EvidenciaDTO evidenciaCruda,
            String tipoAntecedente,
            String contextoEsperado,
            java.util.Map<Integer, String> paginasTexto) {

        ResultadoVerificacionEvidencia res = verificar(
                evidenciaCruda,
                tipoAntecedente,
                contextoEsperado,
                paginasTexto
        );

        if (res.verificada()) {
            return res.evidencia();
        }

        Integer pagina = evidenciaCruda != null ? evidenciaCruda.pagina() : null;
        String fragmento = (evidenciaCruda != null && evidenciaCruda.fragmento() != null)
                ? evidenciaCruda.fragmento().trim()
                : "";
        return new EvidenciaDTO(pagina, fragmento, tipoAntecedente, ESTADO_NO_VERIFICABLE);
    }

    /**
     * Verifica una cita textual contra el mapa de páginas del documento y valida
     * que guarde coherencia temática con el antecedente que pretende respaldar.
     */
    public ResultadoVerificacionEvidencia verificar(
            EvidenciaDTO evidenciaCruda,
            String tipoAntecedente,
            String contextoEsperado,
            java.util.Map<Integer, String> paginasTexto) {

        if (evidenciaCruda == null || evidenciaCruda.fragmento() == null || evidenciaCruda.fragmento().isBlank()) {
            return new ResultadoVerificacionEvidencia(
                    new EvidenciaDTO(null, "", tipoAntecedente, ESTADO_NO_VERIFICABLE),
                    false, null, null, "No se proporcionó fragmento textual."
            );
        }

        if (paginasTexto == null || paginasTexto.isEmpty()) {
            return new ResultadoVerificacionEvidencia(
                    new EvidenciaDTO(evidenciaCruda.pagina(), evidenciaCruda.fragmento().trim(), tipoAntecedente, ESTADO_NO_VERIFICABLE),
                    false, null, null, "No hay texto de páginas disponible para verificar."
            );
        }

        String fragmentoOriginal = evidenciaCruda.fragmento().trim();
        TextoMapeado fragMap = normalizarConMapa(fragmentoOriginal);
        if (fragMap.normalizado().length() < 4) {
            return new ResultadoVerificacionEvidencia(
                    new EvidenciaDTO(evidenciaCruda.pagina(), fragmentoOriginal, tipoAntecedente, ESTADO_NO_VERIFICABLE),
                    false, null, null, "El fragmento citado es demasiado corto para constituir evidencia verificable."
            );
        }

        Integer paginaIndicada = evidenciaCruda.pagina();

        // 1. Verificar primero en la página exacta reportada por la IA
        if (paginaIndicada != null && paginasTexto.containsKey(paginaIndicada)) {
            int[] rango = buscarSubcadenaContigua(paginasTexto.get(paginaIndicada), fragMap.normalizado());
            if (rango != null) {
                if (!tieneCoherenciaContextual(fragmentoOriginal, contextoEsperado)) {
                    return new ResultadoVerificacionEvidencia(
                            new EvidenciaDTO(paginaIndicada, fragmentoOriginal, tipoAntecedente, ESTADO_SIN_CONTEXTO),
                            false, rango[0], rango[1],
                            "La cita existe en la página " + paginaIndicada + ", pero no respalda el antecedente declarado."
                    );
                }
                return new ResultadoVerificacionEvidencia(
                        new EvidenciaDTO(paginaIndicada, fragmentoOriginal, tipoAntecedente, ESTADO_VERIFICADO),
                        true, rango[0], rango[1],
                        "Cita literal verificada en página " + paginaIndicada + " (pos. " + rango[0] + "-" + rango[1] + ")."
                );
            }
        }

        // 2. Si la página es nula o incorrecta, buscar si aparece inequívocamente en una sola página del documento
        var coincidencias = new ArrayList<CoincidenciaPagina>();
        for (Map.Entry<Integer, String> entry : paginasTexto.entrySet()) {
            int[] rango = buscarSubcadenaContigua(entry.getValue(), fragMap.normalizado());
            if (rango != null) {
                coincidencias.add(new CoincidenciaPagina(entry.getKey(), rango));
            }
        }

        if (coincidencias.size() == 1) {
            int paginaReal = coincidencias.get(0).pagina();
            int[] rango = coincidencias.get(0).rango();
            if (!tieneCoherenciaContextual(fragmentoOriginal, contextoEsperado)) {
                return new ResultadoVerificacionEvidencia(
                        new EvidenciaDTO(paginaReal, fragmentoOriginal, tipoAntecedente, ESTADO_SIN_CONTEXTO),
                        false, rango[0], rango[1],
                        "La cita se ubicó en página " + paginaReal + ", pero no guarda relación con el antecedente."
                );
            }
            return new ResultadoVerificacionEvidencia(
                    new EvidenciaDTO(paginaReal, fragmentoOriginal, tipoAntecedente, ESTADO_VERIFICADO),
                    true, rango[0], rango[1],
                    "Página corregida determinísticamente a " + paginaReal + " (pos. " + rango[0] + "-" + rango[1] + ")."
            );
        }

        if (coincidencias.size() > 1) {
            return new ResultadoVerificacionEvidencia(
                    new EvidenciaDTO(paginaIndicada, fragmentoOriginal, tipoAntecedente, ESTADO_AMBIGUO_PAGINAS),
                    false, null, null,
                    "La cita aparece duplicada en múltiples páginas y la página indicada no coincide; requiere revisión humana."
            );
        }

        // 3. No existe coincidencia contigua (se rechazan palabras sueltas dispersas)
        return new ResultadoVerificacionEvidencia(
                new EvidenciaDTO(paginaIndicada, fragmentoOriginal, tipoAntecedente, ESTADO_NO_VERIFICABLE),
                false, null, null,
                "El fragmento citado no coincide de forma contigua con el texto extraído del PDF."
        );
    }

    /**
     * Regla crítica: un requisito jamás puede mantenerse como ENCONTRADO si su evidencia no es verificable.
     */
    public String aplicarReglaDegradacionEstado(String estadoRequisito, ResultadoVerificacionEvidencia verificacion) {
        if ("ENCONTRADO".equals(estadoRequisito) && (verificacion == null || !verificacion.verificada())) {
            return "REQUIERE_VERIFICACION";
        }
        return estadoRequisito;
    }

    private int[] buscarSubcadenaContigua(String textoPagina, String fragmentoNormalizado) {
        if (textoPagina == null || textoPagina.isBlank()) return null;
        TextoMapeado pagMap = normalizarConMapa(textoPagina);
        int idx = pagMap.normalizado().indexOf(fragmentoNormalizado);
        if (idx < 0) return null;
        int finNorm = idx + fragmentoNormalizado.length() - 1;
        int inicioOrig = pagMap.mapaIndicesOriginal()[idx];
        int finOrig = pagMap.mapaIndicesOriginal()[finNorm] + 1;
        return new int[]{inicioOrig, finOrig};
    }

    private boolean tieneCoherenciaContextual(String fragmento, String contextoEsperado) {
        if (contextoEsperado == null || contextoEsperado.isBlank()) {
            return true;
        }
        Set<String> tokensContexto = extraerRaicesSignificativas(contextoEsperado);
        if (tokensContexto.isEmpty()) {
            return true;
        }
        Set<String> tokensFragmento = extraerRaicesSignificativas(fragmento);
        for (String raizCtx : tokensContexto) {
            for (String raizFrag : tokensFragmento) {
                if (raizCtx.equals(raizFrag)
                        || (raizCtx.length() >= 5 && raizFrag.startsWith(raizCtx.substring(0, 5)))
                        || (raizFrag.length() >= 5 && raizCtx.startsWith(raizFrag.substring(0, 5)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> extraerRaicesSignificativas(String texto) {
        String norm = normalizarConMapa(texto).normalizado();
        Set<String> stop = Set.of("para", "como", "con", "sin", "por", "los", "las", "del", "una", "uno", "experiencia", "requisito", "cargo", "anos", "meses");
        Set<String> raices = new HashSet<>();
        for (String t : norm.split(" ")) {
            if (t.length() >= 4 && !stop.contains(t)) {
                raices.add(t);
            }
        }
        return raices;
    }

    private TextoMapeado normalizarConMapa(String original) {
        String sinCorteGuion = original.replaceAll("([\\p{L}])-\\s*\\r?\\n\\s*([\\p{L}])", "$1$2");
        StringBuilder sb = new StringBuilder(sinCorteGuion.length());
        int[] mapa = new int[sinCorteGuion.length() * 2 + 1];
        int posMapa = 0;
        boolean ultimoFueEspacio = true;

        for (int i = 0; i < sinCorteGuion.length(); i++) {
            char c = sinCorteGuion.charAt(i);
            if (c == '\u00AD') continue;

            char unificado = switch (c) {
                case '“', '”', '«', '»' -> '"';
                case '‘', '’' -> '\'';
                case '–', '—', '−' -> '-';
                case '\u00A0', '\n', '\r', '\t' -> ' ';
                default -> c;
            };

            String descompuesto = Normalizer.normalize(String.valueOf(unificado), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");

            for (int j = 0; j < descompuesto.length(); j++) {
                char ch = Character.toLowerCase(descompuesto.charAt(j));
                if (Character.isWhitespace(ch) || ch == '-' || ch == '/' || ch == ',' || ch == ';' || ch == ':' || ch == '.' || ch == '(' || ch == ')') {
                    if (!ultimoFueEspacio && sb.length() > 0) {
                        sb.append(' ');
                        mapa[posMapa++] = i;
                        ultimoFueEspacio = true;
                    }
                } else if (Character.isLetterOrDigit(ch)) {
                    sb.append(ch);
                    mapa[posMapa++] = i;
                    ultimoFueEspacio = false;
                }
            }
        }

        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.deleteCharAt(sb.length() - 1);
            posMapa--;
        }
        return new TextoMapeado(sb.toString(), Arrays.copyOf(mapa, posMapa));
    }
}