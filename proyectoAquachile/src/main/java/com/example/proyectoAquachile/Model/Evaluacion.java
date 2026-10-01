package com.example.proyectoAquachile.Model; // Ajusta a tu paquete real

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "evaluaciones")
public class Evaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación: Una evaluación pertenece a una sola solicitud
    @OneToOne
    @JoinColumn(name = "solicitud_id", nullable = false)
    private SolicitudEvaluacion solicitud;

    @Column(name = "fecha_evaluacion")
    private LocalDate fechaEvaluacion;

    private String resultado;

    // Ejemplo: Recomendado, No recomendado
    @Column(name = "estado_evaluacion")
    private String estadoEvaluacion;

    public Evaluacion() {
        this.fechaEvaluacion = LocalDate.now();
    }

    // --- GETTERS Y SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SolicitudEvaluacion getSolicitud() { return solicitud; }
    public void setSolicitud(SolicitudEvaluacion solicitud) { this.solicitud = solicitud; }

    public LocalDate getFechaEvaluacion() { return fechaEvaluacion; }
    public void setFechaEvaluacion(LocalDate fechaEvaluacion) { this.fechaEvaluacion = fechaEvaluacion; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getEstadoEvaluacion() { return estadoEvaluacion; }
    public void setEstadoEvaluacion(String estadoEvaluacion) { this.estadoEvaluacion = estadoEvaluacion; }
}