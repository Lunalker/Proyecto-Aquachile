package com.example.proyectoAquachile.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Historial trazable de análisis de CV asistidos por IA y su flujo de revisión humana.
 * Separa la salida automática del modelo de los antecedentes corregidos y validados por el analista.
 */
@Entity
@Table(name = "analisis_cv", indexes = {
        @Index(name = "idx_analisis_cv_candidato", columnList = "candidato_id"),
        @Index(name = "idx_analisis_cv_estado", columnList = "estado_analisis, estado_revision")
})
public class AnalisisCv {

    public static final String ANALISIS_GENERADO = "GENERADO";
    public static final String ANALISIS_INVALIDADO = "INVALIDADO";

    public static final String REVISION_PENDIENTE = "PENDIENTE_REVISION";
    public static final String REVISION_EN_CURSO = "EN_REVISION";
    public static final String REVISION_VALIDADO = "VALIDADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private DocumentoCandidato documento;

    @Column(name = "candidato_id", nullable = false)
    private Long candidatoId;

    @Column(name = "nombre_archivo_analizado", nullable = false, length = 255)
    private String nombreArchivoAnalizado;

    @Column(name = "hash_documento", nullable = false, length = 64)
    private String hashDocumento;

    @Column(name = "modelo_utilizado", nullable = false, length = 100)
    private String modeloUtilizado;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    /** Salida estructurada original generada por la IA (inmutable tras la generación). */
    @Column(name = "resultado_ia_json", nullable = false, columnDefinition = "LONGTEXT")
    private String resultadoIaJson;

    /** Estructura revisada/corregida por el analista humano. */
    @Column(name = "datos_validados_json", columnDefinition = "LONGTEXT")
    private String datosValidadosJson;

    @Column(name = "estado_analisis", nullable = false, length = 30)
    private String estadoAnalisis = ANALISIS_GENERADO;

    @Column(name = "estado_revision", nullable = false, length = 30)
    private String estadoRevision = REVISION_PENDIENTE;

    @Column(name = "usuario_validador", length = 120)
    private String usuarioValidador;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(name = "observaciones_analista", columnDefinition = "TEXT")
    private String observacionesAnalista;

    @Column(name = "preguntas_apoyo_json", columnDefinition = "LONGTEXT")
    private String preguntasApoyoJson;

    public Long getId() { return id; }
    public DocumentoCandidato getDocumento() { return documento; }
    public void setDocumento(DocumentoCandidato documento) { this.documento = documento; }
    public Long getCandidatoId() { return candidatoId; }
    public void setCandidatoId(Long candidatoId) { this.candidatoId = candidatoId; }
    public String getNombreArchivoAnalizado() { return nombreArchivoAnalizado; }
    public void setNombreArchivoAnalizado(String nombreArchivoAnalizado) { this.nombreArchivoAnalizado = nombreArchivoAnalizado; }
    public String getHashDocumento() { return hashDocumento; }
    public void setHashDocumento(String hashDocumento) { this.hashDocumento = hashDocumento; }
    public String getModeloUtilizado() { return modeloUtilizado; }
    public void setModeloUtilizado(String modeloUtilizado) { this.modeloUtilizado = modeloUtilizado; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public void setFechaGeneracion(LocalDateTime fechaGeneracion) { this.fechaGeneracion = fechaGeneracion; }
    public String getResultadoIaJson() { return resultadoIaJson; }
    public void setResultadoIaJson(String resultadoIaJson) { this.resultadoIaJson = resultadoIaJson; }
    public String getDatosValidadosJson() { return datosValidadosJson; }
    public void setDatosValidadosJson(String datosValidadosJson) { this.datosValidadosJson = datosValidadosJson; }
    public String getEstadoAnalisis() { return estadoAnalisis; }
    public void setEstadoAnalisis(String estadoAnalisis) { this.estadoAnalisis = estadoAnalisis; }
    public String getEstadoRevision() { return estadoRevision; }
    public void setEstadoRevision(String estadoRevision) { this.estadoRevision = estadoRevision; }
    public String getUsuarioValidador() { return usuarioValidador; }
    public void setUsuarioValidador(String usuarioValidador) { this.usuarioValidador = usuarioValidador; }
    public LocalDateTime getFechaValidacion() { return fechaValidacion; }
    public void setFechaValidacion(LocalDateTime fechaValidacion) { this.fechaValidacion = fechaValidacion; }
    public String getObservacionesAnalista() { return observacionesAnalista; }
    public void setObservacionesAnalista(String observacionesAnalista) { this.observacionesAnalista = observacionesAnalista; }
    public String getPreguntasApoyoJson() { return preguntasApoyoJson; }
    public void setPreguntasApoyoJson(String preguntasApoyoJson) { this.preguntasApoyoJson = preguntasApoyoJson; }
}