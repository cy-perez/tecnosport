package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proveedor")
public class ProveedorJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String nombre;

  @Column(nullable = false)
  private String linea;

  @Column(name = "telefono_whatsapp", nullable = false)
  private String telefonoWhatsapp;

  @Column(name = "nombre_en_exportacion", nullable = false)
  private String nombreEnExportacion;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "publicacion_automatica", nullable = false)
  private boolean publicacionAutomatica;

  @Column(name = "factor_de_margen")
  private BigDecimal factorDeMargen;

  @Column(name = "orden_de_publicacion", nullable = false)
  private String ordenDePublicacion;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected ProveedorJpaEntity() {}

  public ProveedorJpaEntity(
      UUID id,
      String nombre,
      String linea,
      String telefonoWhatsapp,
      String nombreEnExportacion,
      boolean activo,
      boolean publicacionAutomatica,
      BigDecimal factorDeMargen,
      String ordenDePublicacion,
      Instant creadoEn,
      Instant actualizadoEn) {
    this.id = id;
    this.nombre = nombre;
    this.linea = linea;
    this.telefonoWhatsapp = telefonoWhatsapp;
    this.nombreEnExportacion = nombreEnExportacion;
    this.activo = activo;
    this.publicacionAutomatica = publicacionAutomatica;
    this.factorDeMargen = factorDeMargen;
    this.ordenDePublicacion = ordenDePublicacion;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getLinea() {
    return linea;
  }

  public String getTelefonoWhatsapp() {
    return telefonoWhatsapp;
  }

  public String getNombreEnExportacion() {
    return nombreEnExportacion;
  }

  public boolean isActivo() {
    return activo;
  }

  public boolean isPublicacionAutomatica() {
    return publicacionAutomatica;
  }

  public BigDecimal getFactorDeMargen() {
    return factorDeMargen;
  }

  public String getOrdenDePublicacion() {
    return ordenDePublicacion;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
