package com.example.proyectoAquachile.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Criterios profesionales definidos por personal autorizado de AquaChile para contrastar CVs.
 * La IA nunca inventa requisitos; únicamente contrasta evidencias contra esta matriz.
 */
@Entity
@Table(name = "requisitos_cargo")
public class RequisitoCargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String cargo;

    @Column(name = "familia_cargo", length = 120)
    private String familiaCargo;

    @Column(length = 60)
    private String categoria = "EXPERIENCIA";

    @Column(name = "nombre_requisito", nullable = false, length = 200)
    private String nombreRequisito;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "experiencia_minima_meses")
    private Integer experienciaMinimaMeses;

    @Column(nullable = false)
    private boolean obligatorio = true;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_por", length = 120)
    private String creadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public Long getId() { return id; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getFamiliaCargo() { return familiaCargo; }
    public void setFamiliaCargo(String familiaCargo) { this.familiaCargo = familiaCargo; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getNombreRequisito() { return nombreRequisito; }
    public void setNombreRequisito(String nombreRequisito) { this.nombreRequisito = nombreRequisito; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getExperienciaMinimaMeses() { return experienciaMinimaMeses; }
    public void setExperienciaMinimaMeses(Integer experienciaMinimaMeses) { this.experienciaMinimaMeses = experienciaMinimaMeses; }
    public boolean isObligatorio() { return obligatorio; }
    public void setObligatorio(boolean obligatorio) { this.obligatorio = obligatorio; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}