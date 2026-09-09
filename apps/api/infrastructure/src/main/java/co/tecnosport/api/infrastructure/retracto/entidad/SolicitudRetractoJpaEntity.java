package co.tecnosport.api.infrastructure.retracto.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** El reembolso son columnas de esta misma fila: es un valor del agregado, no una entidad. */
@Entity
@Table(name = "solicitud_retracto")
public class SolicitudRetractoJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(name = "radicada_en", nullable = false)
  private Instant radicadaEn;

  @Column(name = "radicada_por", nullable = false)
  private String radicadaPor;

  @Column private String motivo;

  @Column(name = "verdicto_plazo", nullable = false)
  private String verdictoPlazo;

  @Column(nullable = false)
  private String estado;

  @Column(name = "producto_recibido_en")
  private Instant productoRecibidoEn;

  @Column(name = "reembolso_monto")
  private BigDecimal reembolsoMonto;

  @Column(name = "reembolso_medio")
  private String reembolsoMedio;

  @Column(name = "reembolso_comprobante")
  private String reembolsoComprobante;

  @Column(name = "reembolso_registrado_en")
  private Instant reembolsoRegistradoEn;

  @Column(name = "reembolso_registrado_por")
  private String reembolsoRegistradoPor;

  protected SolicitudRetractoJpaEntity() {}

  public SolicitudRetractoJpaEntity(
      UUID id,
      UUID pedidoId,
      Instant radicadaEn,
      String radicadaPor,
      String motivo,
      String verdictoPlazo,
      String estado,
      Instant productoRecibidoEn,
      BigDecimal reembolsoMonto,
      String reembolsoMedio,
      String reembolsoComprobante,
      Instant reembolsoRegistradoEn,
      String reembolsoRegistradoPor) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.radicadaEn = radicadaEn;
    this.radicadaPor = radicadaPor;
    this.motivo = motivo;
    this.verdictoPlazo = verdictoPlazo;
    this.estado = estado;
    this.productoRecibidoEn = productoRecibidoEn;
    this.reembolsoMonto = reembolsoMonto;
    this.reembolsoMedio = reembolsoMedio;
    this.reembolsoComprobante = reembolsoComprobante;
    this.reembolsoRegistradoEn = reembolsoRegistradoEn;
    this.reembolsoRegistradoPor = reembolsoRegistradoPor;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public Instant getRadicadaEn() {
    return radicadaEn;
  }

  public String getRadicadaPor() {
    return radicadaPor;
  }

  public String getMotivo() {
    return motivo;
  }

  public String getVerdictoPlazo() {
    return verdictoPlazo;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getProductoRecibidoEn() {
    return productoRecibidoEn;
  }

  public BigDecimal getReembolsoMonto() {
    return reembolsoMonto;
  }

  public String getReembolsoMedio() {
    return reembolsoMedio;
  }

  public String getReembolsoComprobante() {
    return reembolsoComprobante;
  }

  public Instant getReembolsoRegistradoEn() {
    return reembolsoRegistradoEn;
  }

  public String getReembolsoRegistradoPor() {
    return reembolsoRegistradoPor;
  }
}
