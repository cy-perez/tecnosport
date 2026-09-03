package co.tecnosport.api.infrastructure.pago.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pago")
public class PagoJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(nullable = false, unique = true)
  private String referencia;

  @Column(name = "metodo_pago", nullable = false)
  private String metodoPago;

  @Column(nullable = false)
  private BigDecimal monto;

  @Column(nullable = false)
  private String estado;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  protected PagoJpaEntity() {}

  public PagoJpaEntity(
      UUID id,
      UUID pedidoId,
      String referencia,
      String metodoPago,
      BigDecimal monto,
      String estado,
      Instant creadoEn,
      Instant actualizadoEn) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.referencia = referencia;
    this.metodoPago = metodoPago;
    this.monto = monto;
    this.estado = estado;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getReferencia() {
    return referencia;
  }

  public String getMetodoPago() {
    return metodoPago;
  }

  public BigDecimal getMonto() {
    return monto;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
