package com.example.proyectoAquachile.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ValidadorArchivoCvUtil {

    public static final long MAX_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> EXTENSIONES_ADMITIDAS = Set.of("pdf", "doc", "docx");

    private ValidadorArchivoCvUtil() {}

    public record ArchivoCvValidado(
            String nombreSeguro,
            String tipoMimeDetectado,
            long tamanoBytes,
            String hashSha256,
            byte[] contenido
    ) {}

    public static ArchivoCvValidado validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes adjuntar un archivo CV.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo CV supera el límite máximo de 5 MB.");
        }

        String nombreLimpio = sanitizarNombreArchivo(archivo.getOriginalFilename());
        int punto = nombreLimpio.lastIndexOf('.');
        if (punto <= 0 || punto == nombreLimpio.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe tener una extensión válida (.pdf, .doc o .docx).");
        }

        String extension = nombreLimpio.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES_ADMITIDAS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato no admitido. Solo se aceptan archivos PDF, DOC o DOCX.");
        }

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el contenido del archivo adjunto.");
        }

        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CV debe ocupar entre 1 byte y 5 MB.");
        }

        String mimeDetectado = detectarMimePorFirmaBinaria(extension, bytes);
        String sha256 = calcularSha256(bytes);

        return new ArchivoCvValidado(nombreLimpio, mimeDetectado, bytes.length, sha256, bytes);
    }

    public static String sanitizarNombreArchivo(String original) {
        if (original == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el nombre del archivo.");
        }
        String normalizado = original.replace('\\', '/');
        String base = normalizado.substring(normalizado.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .replaceAll("[<>:\"|?*]", "_")
                .trim();

        if (base.isBlank() || base.equals(".") || base.equals("..") || base.contains("..") || base.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del archivo contiene caracteres o rutas no permitidas.");
        }
        return base;
    }

    public static String detectarMimePorFirmaBinaria(String extension, byte[] bytes) {
        if ("pdf".equals(extension)
                && bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') {
            return "application/pdf";
        }
        if ("docx".equals(extension) && esDocxAutentico(bytes)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if ("doc".equals(extension)
                && bytes.length >= 8
                && (bytes[0] & 0xff) == 0xd0 && (bytes[1] & 0xff) == 0xcf
                && (bytes[2] & 0xff) == 0x11 && (bytes[3] & 0xff) == 0xe0
                && (bytes[4] & 0xff) == 0xa1 && (bytes[5] & 0xff) == 0xb1
                && (bytes[6] & 0xff) == 0x1a && (bytes[7] & 0xff) == 0xe1) {
            return "application/msword";
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Firma binaria inválida: el archivo no corresponde a un PDF, DOC o DOCX auténtico."
        );
    }

    private static boolean esDocxAutentico(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entrada;
            int entradas = 0;
            boolean tieneContentTypes = false;
            boolean tieneDocumentXml = false;
            while ((entrada = zip.getNextEntry()) != null && ++entradas <= 300) {
                String nombreEntrada = entrada.getName();
                if ("[Content_Types].xml".equals(nombreEntrada)) {
                    tieneContentTypes = true;
                } else if ("word/document.xml".equals(nombreEntrada)) {
                    tieneDocumentXml = true;
                }
                if (tieneContentTypes && tieneDocumentXml) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static String calcularSha256(byte[] datos) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en JVM.", e);
        }
    }
}