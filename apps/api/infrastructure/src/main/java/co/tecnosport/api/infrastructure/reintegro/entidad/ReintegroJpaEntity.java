package co.tecnosport.api.infrastructure.reintegro.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila inmutable: un reintegro se registra una vez y no cambia, solo se consulta. */
@Entity
@Table(name = "reintegro")
public class ReintegroJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(nullable = false)
  private String motivo;

  @Column(name = "origen_id", nullable = false)
  private UUID origenId;

  @Column(nullable = false)
  private BigDecimal monto;

  @Column(nullable = false)
  private String medio;

  @Column private String comprobante;

  @Column(name = "registrado_en", nullable = false)
  private Instant registradoEn;

  @Column(name = "registrado_por", nullable = false)
  private String registradoPor;

  protected ReintegroJpaEntity() {}

  public ReintegroJpaEntity(
      UUID id,
      UUID pedidoId,
      String motivo,
      UUID origenId,
      BigDecimal monto,
      String medio,
      String comprobante,
      Instant registradoEn,
      String registradoPor) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.motivo = motivo;
    this.origenId = origenId;
    this.monto = monto;
    this.medio = medio;
    this.comprobante = comprobante;
    this.registradoEn = registradoEn;
    this.registradoPor = registradoPor;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getMotivo() {
    return motivo;
  }

  public UUID getOrigenId() {
    return origenId;
  }

  public BigDecimal getMonto() {
    return monto;
  }

  public String getMedio() {
    return medio;
  }

  public String getComprobante() {
    return comprobante;
  }

  public Instant getRegistradoEn() {
    return registradoEn;
  }

  public String getRegistradoPor() {
    return registradoPor;
  }
}
