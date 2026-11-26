package com.example.proyectoAquachile.Service.ia;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extrae texto de currículums PDF conservando la numeración original de páginas,
 * normalizando saltos de línea/guiones y controlando límites de tamaño y páginas sin texto.
 */
@Service
public class ExtractorPdfService {

    public static final int MAX_PAGINAS_DEFAULT = 15;
    public static final int MAX_CARACTERES_POR_BLOQUE = 24_000;
    public static final int MAX_CARACTERES_DOCUMENTO = 72_000;
    public static final int MAX_BYTES_PDF = 5 * 1024 * 1024;

    public record DocumentoExtraido(
            java.util.Map<Integer, String> paginasTexto,
            boolean truncado,
            List<Integer> paginasSinTexto
    ) {}

    public ExtractorPdfService() {
    }

    public DocumentoExtraido extraerDocumento(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 5
                || pdfBytes[0] != '%' || pdfBytes[1] != 'P' || pdfBytes[2] != 'D'
                || pdfBytes[3] != 'F' || pdfBytes[4] != '-') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo no corresponde a un documento PDF auténtico.");
        }
        if (pdfBytes.length > MAX_BYTES_PDF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El documento PDF supera el tamaño máximo permitido de 5 MB.");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPaginas = document.getNumberOfPages();
            if (totalPaginas <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El PDF está vacío y no contiene páginas analizables.");
            }
            if (totalPaginas > MAX_PAGINAS_DEFAULT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El PDF supera el límite de " + MAX_PAGINAS_DEFAULT + " páginas permitido para análisis.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            java.util.Map<Integer, String> paginas = new LinkedHashMap<>();
            List<Integer> paginasSinTexto = new ArrayList<>();
            int acumulado = 0;
            boolean truncado = false;

            for (int p = 1; p <= totalPaginas; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String limpio = limpiarTextoPagina(stripper.getText(document));

                if (limpio.isBlank()) {
                    paginasSinTexto.add(p);
                    paginas.put(p, "");
                    continue;
                }

                if (acumulado + limpio.length() > MAX_CARACTERES_DOCUMENTO) {
                    int disponible = Math.max(0, MAX_CARACTERES_DOCUMENTO - acumulado);
                    limpio = limpio.substring(0, disponible);
                    truncado = true;
                }

                paginas.put(p, limpio);
                acumulado += limpio.length();

                if (truncado) {
                    break;
                }
            }

            boolean hayTexto = paginas.values().stream().anyMatch(t -> !t.isBlank());
            if (!hayTexto) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El PDF no contiene texto seleccionable (documento escaneado como imagen; requiere revisión manual).");
            }

            return new DocumentoExtraido(paginas, truncado, paginasSinTexto);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (e.getClass().getSimpleName().contains("Password")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El archivo PDF está protegido con contraseña y no puede leerse.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo leer el archivo PDF. Verifica que no esté dañado.");
        }
    }

    public List<java.util.Map<Integer, String>> dividirEnBloques(java.util.Map<Integer, String> paginasTexto) {
        if (paginasTexto == null || paginasTexto.isEmpty()) {
            return List.of();
        }
        var bloques = new ArrayList<java.util.Map<Integer, String>>();
        java.util.Map<Integer, String> bloqueActual = new LinkedHashMap<>();
        int charsBloque = 0;

        for (Map.Entry<Integer, String> entry : paginasTexto.entrySet()) {
            String texto = entry.getValue() == null ? "" : entry.getValue();
            if (!bloqueActual.isEmpty() && (charsBloque + texto.length() > MAX_CARACTERES_POR_BLOQUE)) {
                bloques.add(bloqueActual);
                bloqueActual = new LinkedHashMap<>();
                charsBloque = 0;
            }
            bloqueActual.put(entry.getKey(), texto);
            charsBloque += texto.length();
        }

        if (!bloqueActual.isEmpty()) {
            bloques.add(bloqueActual);
        }
        return bloques;
    }

    private String limpiarTextoPagina(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("([\\p{L}])-\\s*\\r?\\n\\s*([\\p{L}])", "$1$2")
                .replace("\r\n", "\n")
                .trim();
    }
}