package com.example.proyectoAquachile.Model;

import jakarta.persistence.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "postulaciones",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_postulacion_candidato_oferta",
                        columnNames = {"candidato_id", "oferta_laboral_id"}
                )
        },
        indexes = {
                @Index(name = "idx_postulacion_candidato", columnList = "candidato_id"),
                @Index(name = "idx_postulacion_oferta", columnList = "oferta_laboral_id"),
                @Index(name = "idx_postulacion_contenedor_uuid", columnList = "contenedor_uuid")
        }
)
public class Postulacion {

    public static final String ESTADO_RECIBIDA = "RECIBIDA";
    public static final String ESTADO_EN_REVISION = "EN_REVISION";
    public static final String ESTADO_EVALUADA = "EVALUADA";
    public static final String ESTADO_DESCARTADA = "DESCARTADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_laboral_id", nullable = false)
    private OfertaLaboral ofertaLaboral;

    @Column(name = "fecha_postulacion", nullable = false)
    private LocalDateTime fechaPostulacion;

    @Column(nullable = false, length = 40)
    private String estado = ESTADO_RECIBIDA;

    @Column(name = "contenedor_uuid", nullable = false, unique = true, length = 36)
    private String contenedorUuid;

    @Column(name = "nombre_contenedor", nullable = false, unique = true, length = 160)
    private String nombreContenedor;

    @OneToOne(mappedBy = "postulacion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DocumentoPostulacion documento;

    public Postulacion() {}

    /**
     * Construye un nombre descriptivo de contenedor basado en el nombre del candidato y su UUID,
     * eliminando cualquier carácter de ruta o separador de directorio.
     */
    public static String generarNombreContenedorSeguro(String nombreCandidato, String uuid) {
        String base = (nombreCandidato == null || nombreCandidato.isBlank()) ? "postulante" : nombreCandidato;
        String sinAcentos = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "postulante";
        }
        if (slug.length() > 45) {
            slug = slug.substring(0, 45).replaceAll("-+$", "");
        }
        String uuidLimpio = uuid.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return "cv-" + slug + "-" + uuidLimpio;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidato getCandidato() { return candidato; }
    public void setCandidato(Candidato candidato) { this.candidato = candidato; }

    public OfertaLaboral getOfertaLaboral() { return ofertaLaboral; }
    public void setOfertaLaboral(OfertaLaboral ofertaLaboral) { this.ofertaLaboral = ofertaLaboral; }

    public LocalDateTime getFechaPostulacion() { return fechaPostulacion; }
    public void setFechaPostulacion(LocalDateTime fechaPostulacion) { this.fechaPostulacion = fechaPostulacion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getContenedorUuid() { return contenedorUuid; }
    public void setContenedorUuid(String contenedorUuid) { this.contenedorUuid = contenedorUuid; }

    public String getNombreContenedor() { return nombreContenedor; }
    public void setNombreContenedor(String nombreContenedor) { this.nombreContenedor = nombreContenedor; }

    public DocumentoPostulacion getDocumento() { return documento; }
    public void setDocumento(DocumentoPostulacion documento) {
        this.documento = documento;
        if (documento != null && documento.getPostulacion() != this) {
            documento.setPostulacion(this);
        }
    }
}