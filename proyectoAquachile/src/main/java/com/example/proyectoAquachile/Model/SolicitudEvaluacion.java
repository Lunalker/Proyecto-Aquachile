package com.example.proyectoAquachile.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "solicitudes_evaluacion", indexes = {
        @Index(name = "idx_solicitud_candidato", columnList = "candidato_id"),
        @Index(name = "idx_solicitud_postulacion", columnList = "postulacion_id")
})
public class SolicitudEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postulacion_id")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Postulacion postulacion;

    @Transient
    private Long postulacionIdInput;

    private String cargo;

    @Column(name = "familia_cargo")
    private String familiaCargo;

    @Column(name = "fecha_solicitud")
    private LocalDate fechaSolicitud;

    private String estado;

    @Column(name = "profesional_responsable")
    private String profesionalResponsable;

    private String observaciones;

    public SolicitudEvaluacion() {
        this.fechaSolicitud = LocalDate.now();
        this.estado = "Pendiente";
    }

    @JsonProperty("postulacionId")
    public Long getPostulacionId() {
        if (postulacion != null && postulacion.getId() != null) {
            return postulacion.getId();
        }
        return postulacionIdInput;
    }

    @JsonProperty("postulacionId")
    public void setPostulacionId(Long postulacionId) {
        this.postulacionIdInput = postulacionId;
    }

    // --- GETTERS Y SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidato getCandidato() { return candidato; }
    public void setCandidato(Candidato candidato) { this.candidato = candidato; }

    public Postulacion getPostulacion() { return postulacion; }
    public void setPostulacion(Postulacion postulacion) {
        this.postulacion = postulacion;
        this.postulacionIdInput = (postulacion != null) ? postulacion.getId() : null;
    }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public String getFamiliaCargo() { return familiaCargo; }
    public void setFamiliaCargo(String familiaCargo) { this.familiaCargo = familiaCargo; }

    public LocalDate getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDate fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getProfesionalResponsable() { return profesionalResponsable; }
    public void setProfesionalResponsable(String profesionalResponsable) { this.profesionalResponsable = profesionalResponsable; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}