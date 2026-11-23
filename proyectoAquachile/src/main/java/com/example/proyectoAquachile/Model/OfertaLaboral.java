package com.example.proyectoAquachile.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ofertas_laborales", indexes = {
        @Index(name = "idx_oferta_estado_fecha", columnList = "estado_publicacion, fecha_publicacion")
})
public class OfertaLaboral {

    public static final String ESTADO_PUBLICADA = "PUBLICADA";
    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String cargo;

    @Column(nullable = false, length = 120)
    private String categoria;

    @Column(nullable = false, length = 150)
    private String ubicacion;

    @Column(nullable = false, length = 60)
    private String modalidad;

    @Column(name = "fecha_publicacion", nullable = false)
    private LocalDateTime fechaPublicacion;

    @Column(nullable = false)
    private Integer cupos = 1;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String funciones;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String requisitos;

    @Column(columnDefinition = "TEXT")
    private String beneficios;

    @Column(name = "rango_salarial", length = 120)
    private String rangoSalarial;

    @Column(name = "estado_publicacion", nullable = false, length = 30)
    private String estadoPublicacion = ESTADO_PUBLICADA;

    public OfertaLaboral() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }

    public LocalDateTime getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(LocalDateTime fechaPublicacion) { this.fechaPublicacion = fechaPublicacion; }

    public Integer getCupos() { return cupos; }
    public void setCupos(Integer cupos) { this.cupos = cupos; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getFunciones() { return funciones; }
    public void setFunciones(String funciones) { this.funciones = funciones; }

    public String getRequisitos() { return requisitos; }
    public void setRequisitos(String requisitos) { this.requisitos = requisitos; }

    public String getBeneficios() { return beneficios; }
    public void setBeneficios(String beneficios) { this.beneficios = beneficios; }

    public String getRangoSalarial() { return rangoSalarial; }
    public void setRangoSalarial(String rangoSalarial) { this.rangoSalarial = rangoSalarial; }

    public String getEstadoPublicacion() { return estadoPublicacion; }
    public void setEstadoPublicacion(String estadoPublicacion) { this.estadoPublicacion = estadoPublicacion; }
}