package com.example.proyectoAquachile.Model; // Ajusta a tu paquete real

import com.example.proyectoAquachile.Model.Candidato;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "solicitudes_evaluacion")
public class SolicitudEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación: Muchas solicitudes pueden pertenecer a un mismo candidato
    @ManyToOne
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    private String cargo;

    @Column(name = "familia_cargo")
    private String familiaCargo;

    @Column(name = "fecha_solicitud")
    private LocalDate fechaSolicitud;

    // Estados sugeridos: Pendiente, En proceso, Finalizada
    private String estado;

    @Column(name = "profesional_responsable")
    private String profesionalResponsable;

    private String observaciones;

    public SolicitudEvaluacion() {
        this.fechaSolicitud = LocalDate.now(); // Asigna la fecha actual automáticamente
        this.estado = "Pendiente"; // Estado inicial por defecto
    }

    // --- GETTERS Y SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Candidato getCandidato() { return candidato; }
    public void setCandidato(Candidato candidato) { this.candidato = candidato; }

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