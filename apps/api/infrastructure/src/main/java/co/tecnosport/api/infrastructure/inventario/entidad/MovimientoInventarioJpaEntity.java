package co.tecnosport.api.infrastructure.inventario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventarioJpaEntity {

  @Id private UUID id;

  @Column(name = "inventario_id", nullable = false)
  private UUID inventarioId;

  @Column(nullable = false)
  private String tipo;

  @Column(nullable = false)
  private int cantidad;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "expira_en")
  private Instant expiraEn;

  @Column(name = "referencia_id")
  private UUID referenciaId;

  private String motivo;

  protected MovimientoInventarioJpaEntity() {}

  public MovimientoInventarioJpaEntity(
      UUID id,
      UUID inventarioId,
      String tipo,
      int cantidad,
      Instant creadoEn,
      Instant expiraEn,
      UUID referenciaId,
      String motivo) {
    this.id = id;
    this.inventarioId = inventarioId;
    this.tipo = tipo;
    this.cantidad = cantidad;
    this.creadoEn = creadoEn;
    this.expiraEn = expiraEn;
    this.referenciaId = referenciaId;
    this.motivo = motivo;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInventarioId() {
    return inventarioId;
  }

  public String getTipo() {
    return tipo;
  }

  public int getCantidad() {
    return cantidad;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public UUID getReferenciaId() {
    return referenciaId;
  }

  public String getMotivo() {
    return motivo;
  }
}
