package com.example.proyectoAquachile.Model;

import jakarta.persistence.*;

@Entity
@Table(name = "candidatos")
public class Candidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String correo;

    private String telefono;

    @Column(name = "cargo_postula")
    private String cargoPostula;

    @Column(name = "familia_cargo")
    private String familiaCargo;

    // Campos extra basados en el Excel
    private String origen; // Interno o Externo
    private String ubicacion;
    private String unidad;

    // Constructores vacíos y con parámetros (Obligatorio para JPA)
    public Candidato() {
    }

    // --- GETTERS Y SETTERS ---
    // (Si estás usando Lombok, puedes borrar todos estos getters/setters
    // y solo poner la anotación @Data arriba de public class Candidato)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCargoPostula() { return cargoPostula; }
    public void setCargoPostula(String cargoPostula) { this.cargoPostula = cargoPostula; }

    public String getFamiliaCargo() { return familiaCargo; }
    public void setFamiliaCargo(String familiaCargo) { this.familiaCargo = familiaCargo; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
}