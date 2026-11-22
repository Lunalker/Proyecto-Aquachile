package com.example.proyectoAquachile.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos_postulacion")
public class DocumentoPostulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postulacion_id", nullable = false, unique = true)
    private Postulacion postulacion;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @Column(name = "tipo_mime", nullable = false, length = 120)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] contenido;

    public DocumentoPostulacion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Postulacion getPostulacion() { return postulacion; }
    public void setPostulacion(Postulacion postulacion) { this.postulacion = postulacion; }

    public String getNombreOriginal() { return nombreOriginal; }
    public void setNombreOriginal(String nombreOriginal) { this.nombreOriginal = nombreOriginal; }

    public String getTipoMime() { return tipoMime; }
    public void setTipoMime(String tipoMime) { this.tipoMime = tipoMime; }

    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDateTime fechaCarga) { this.fechaCarga = fechaCarga; }

    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }

    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }
}