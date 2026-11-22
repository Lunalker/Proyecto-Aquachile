package com.example.proyectoAquachile.Service.ia;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorPdfYSeguridadTest {

    private final ExtractorPdfService extractor = new ExtractorPdfService();
    private final AnonimizadorPiiService anonimizador = new AnonimizadorPiiService();

    private byte[] generarPdfConTexto(String... textosPorPagina) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String texto : textosPorPagina) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(texto);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extraePdfValidoYDivideEnBloquesCuandoEsExtenso() throws Exception {
        byte[] pdf = generarPdfConTexto("Pagina uno experiencia", "Pagina dos certificaciones");
        var extraido = extractor.extraerDocumento(pdf);

        assertEquals(2, extraido.paginasTexto().size());
        assertFalse(extraido.truncado());
        assertFalse(extractor.dividirEnBloques(extraido.paginasTexto()).isEmpty());
    }

    @Test
    void rechazaArchivoDanadoProtegidoEscaneadoYExcesoDePaginas() throws Exception {
        // Archivo dañado
        byte[] danado = "%PDF-1.7 contenido corrupto".getBytes(StandardCharsets.UTF_8);
        assertThrows(ResponseStatusException.class, () -> extractor.extraerDocumento(danado));

        // Archivo protegido con contraseña
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            StandardProtectionPolicy spp = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            doc.protect(spp);
            doc.save(out);
            assertThrows(ResponseStatusException.class, () -> extractor.extraerDocumento(out.toByteArray()));
        }

        // Archivo escaneado sin texto seleccionable (422)
        byte[] escaneado = generarPdfConTexto("   ");
        ResponseStatusException exEsc = assertThrows(ResponseStatusException.class,
                () -> extractor.extraerDocumento(escaneado));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exEsc.getStatusCode());

        // Exceso de páginas (> 15)
        String[] dieciseisPaginas = new String[16];
        for (int i = 0; i < 16; i++) dieciseisPaginas[i] = "Contenido pagina " + (i + 1);
        byte[] largo = generarPdfConTexto(dieciseisPaginas);
        assertThrows(ResponseStatusException.class, () -> extractor.extraerDocumento(largo));
    }

    @Test
    void anonimizaDatosPersonalesSinEliminarEmpresasNiCargos() {
        String cv = "Sofía Valenzuela | RUT 19.876.543-2 | sofia.v@gmail.com | +56 9 8765 4321 | " +
                "Dirección: Av. Diego Portales 450, Puerto Montt. " +
                "Cargo: Ingeniera de Procesos en AquaChile S.A., Universidad Austral de Chile.";

        String limpio = anonimizador.anonimizarTextoCv(cv, "Sofía Valenzuela");

        assertFalse(limpio.contains("19.876.543-2"));
        assertFalse(limpio.contains("sofia.v@gmail.com"));
        assertFalse(limpio.contains("8765 4321"));
        assertFalse(limpio.contains("Diego Portales"));
        // Conserva información profesional necesaria
        assertTrue(limpio.contains("Ingeniera de Procesos"));
        assertTrue(limpio.contains("AquaChile"));
        assertTrue(limpio.contains("Universidad Austral de Chile"));

        String obs = anonimizador.anonimizarObservaciones("Llamar al +56912345678 o escribir a juan@correo.cl");
        assertFalse(obs.contains("12345678"));
        assertFalse(obs.contains("juan@correo.cl"));
    }
}