package co.tecnosport.api.infrastructure.proveedores.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publicacion_proveedor")
public class PublicacionProveedorJpaEntity {

  @Id private UUID id;

  @Column(name = "proveedor_id", nullable = false)
  private UUID proveedorId;

  @Column(name = "lote_id", nullable = false)
  private UUID loteId;

  @Column(name = "mensaje_principal_id", nullable = false)
  private UUID mensajePrincipalId;

  @Column(nullable = false)
  private Instant fecha;

  @Column(nullable = false)
  private String estado;

  @Column private String motivo;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected PublicacionProveedorJpaEntity() {}

  public PublicacionProveedorJpaEntity(
      UUID id,
      UUID proveedorId,
      UUID loteId,
      UUID mensajePrincipalId,
      Instant fecha,
      String estado,
      String motivo,
      Instant creadoEn) {
    this.id = id;
    this.proveedorId = proveedorId;
    this.loteId = loteId;
    this.mensajePrincipalId = mensajePrincipalId;
    this.fecha = fecha;
    this.estado = estado;
    this.motivo = motivo;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProveedorId() {
    return proveedorId;
  }

  public UUID getLoteId() {
    return loteId;
  }

  public UUID getMensajePrincipalId() {
    return mensajePrincipalId;
  }

  public Instant getFecha() {
    return fecha;
  }

  public String getEstado() {
    return estado;
  }

  public String getMotivo() {
    return motivo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
