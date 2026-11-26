package com.example.proyectoAquachile.Service;

import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Protege la privacidad del postulante antes de enviar texto o notas a proveedores externos de IA,
 * eliminando identificadores y atributos personales sensibles sin alterar nombres de empresas,
 * instituciones educativas, cargos ni certificaciones.
 */
public final class AnonimizadorCvUtil {

    private static final Pattern PATRON_RUT_ETIQUETADO = Pattern.compile(
            "(?i)\\b(?:r\\.?u\\.?t\\.?|r\\.?u\\.?n\\.?)\\s*:?\\s*\\d{1,2}(?:\\.?\\d{3}){2}\\s*-[\\s]*[\\dkK]\\b");

    private static final Pattern PATRON_RUT = Pattern.compile(
            "\\b\\d{1,2}(?:\\.\\d{3}){2}\\s*-[\\s]*[\\dkK]\\b|\\b\\d{7,8}\\s*-[\\s]*[\\dkK]\\b");

    private static final Pattern PATRON_EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PATRON_TELEFONO_ETIQUETADO = Pattern.compile(
            "(?im)\\b(?:tel[eé]fono|tel\\.?|cel\\.?|celular|m[oó]vil|fono|whatsapp)\\s*:?\\s*\\+?[\\d\\s().-]{7,20}");

    private static final Pattern PATRON_TELEFONO = Pattern.compile(
            "(?:\\+?\\(?56\\)?\\s*)?(?:9\\s*)?\\d{4}[\\s.-]?\\d{4}\\b");

    private static final Pattern PATRON_DIRECCION_LINEA = Pattern.compile(
            "(?im)^\\s*(?:direcci[oó]n|domicilio|residencia|direcci[oó]n\\s+particular)\\s*:.*$");

    // Exige numeración domiciliaria o prefijo explícito de calle para no borrar "Sector Acuícola" o empresas
    private static final Pattern PATRON_CALLE_NUMERADA = Pattern.compile(
            "(?i)\\b(?:av\\.?|avenida|calle|pasaje|psje\\.?|camino|poblaci[oó]n|villa)\\s+[a-záéíóúñ\\s.-]{3,45}(?:n[°ºo]?\\s*)?\\d{1,5}\\b");

    private static final Pattern PATRON_ATRIBUTOS_SENSIBLES = Pattern.compile(
            "(?im)^\\s*(?:fecha\\s+de\\s+nacimiento|edad|estado\\s+civil|nacionalidad|g[eé]nero|sexo|previsi[oó]n|afp|isapre|religi[oó]n|salud|discapacidad)\\s*:.*$");

    private static final Pattern PATRON_NOMBRE_ETIQUETADO = Pattern.compile(
            "(?im)^\\s*(?:nombre\\s+completo|postulante|candidato)\\s*:.*$");

    private static final Set<String> CONECTORES_NOMBRE = Set.of(
            "de", "del", "la", "las", "los", "san", "santa", "van", "von", "mc", "mac"
    );

    private AnonimizadorCvUtil() {}

    public static String anonimizarTexto(String textoOriginal, String nombreCandidato) {
        if (textoOriginal == null || textoOriginal.isBlank()) {
            return "";
        }
        String resultado = textoOriginal;
        resultado = PATRON_RUT_ETIQUETADO.matcher(resultado).replaceAll("[RUT_OMITIDO]");
        resultado = PATRON_RUT.matcher(resultado).replaceAll("[RUT_OMITIDO]");
        resultado = PATRON_EMAIL.matcher(resultado).replaceAll("[EMAIL_OMITIDO]");
        resultado = PATRON_TELEFONO_ETIQUETADO.matcher(resultado).replaceAll("[TELEFONO_OMITIDO]");
        resultado = PATRON_TELEFONO.matcher(resultado).replaceAll("[TELEFONO_OMITIDO]");
        resultado = PATRON_DIRECCION_LINEA.matcher(resultado).replaceAll("[DIRECCION_OMITIDA]");
        resultado = PATRON_CALLE_NUMERADA.matcher(resultado).replaceAll("[DIRECCION_OMITIDA]");
        resultado = PATRON_ATRIBUTOS_SENSIBLES.matcher(resultado).replaceAll("[DATO_PERSONAL_OMITIDO]");
        resultado = PATRON_NOMBRE_ETIQUETADO.matcher(resultado).replaceAll("[CANDIDATO]");

        if (nombreCandidato != null && !nombreCandidato.isBlank()) {
            String limpio = nombreCandidato.trim();
            resultado = reemplazarInsensibleATildes(resultado, limpio, "[CANDIDATO]");
            for (String parte : limpio.split("\\s+")) {
                String normParte = quitarTildes(parte).toLowerCase();
                if (parte.length() >= 3 && !CONECTORES_NOMBRE.contains(normParte)) {
                    resultado = reemplazarPalabraInsensibleATildes(resultado, parte, "[CANDIDATO]");
                }
            }
        }
        return resultado;
    }

    /**
     * Sanitiza observaciones escritas por el analista antes de enviarlas al generador de preguntas.
     */
    public static String anonimizarObservaciones(String observaciones) {
        return anonimizarTexto(observaciones, null);
    }

    private static String reemplazarInsensibleATildes(String texto, String objetivo, String reemplazo) {
        String regex = construirRegexFlexibleTildes(objetivo);
        return Pattern.compile("(?i)" + regex).matcher(texto).replaceAll(reemplazo);
    }

    private static String reemplazarPalabraInsensibleATildes(String texto, String palabra, String reemplazo) {
        String regex = "\\b" + construirRegexFlexibleTildes(palabra) + "\\b";
        return Pattern.compile("(?i)" + regex).matcher(texto).replaceAll(reemplazo);
    }

    private static String construirRegexFlexibleTildes(String literal) {
        String base = quitarTildes(literal);
        StringBuilder sb = new StringBuilder();
        for (char c : base.toCharArray()) {
            switch (Character.toLowerCase(c)) {
                case 'a' -> sb.append("[aáàäâ]");
                case 'e' -> sb.append("[eéèëê]");
                case 'i' -> sb.append("[iíìïî]");
                case 'o' -> sb.append("[oóòöô]");
                case 'u' -> sb.append("[uúùüû]");
                case 'n' -> sb.append("[nñ]");
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    private static String quitarTildes(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public static String calcularSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el hash SHA-256 del documento", e);
        }
    }
}