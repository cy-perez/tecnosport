package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "envio")
public class EnvioJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(name = "despachado_en", nullable = false)
  private Instant despachadoEn;

  @Column(name = "comision_recaudo")
  private BigDecimal comisionRecaudo;

  @Column(name = "recaudo_conciliado_en")
  private Instant recaudoConciliadoEn;

  /** Texto y no un enum de JPA: el check de la V52 es el que acota los valores. */
  @Column(name = "modalidad_recaudo")
  private String modalidadRecaudo;

  protected EnvioJpaEntity() {}

  public EnvioJpaEntity(
      UUID id,
      UUID pedidoId,
      Instant despachadoEn,
      BigDecimal comisionRecaudo,
      Instant recaudoConciliadoEn,
      String modalidadRecaudo) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.despachadoEn = despachadoEn;
    this.comisionRecaudo = comisionRecaudo;
    this.recaudoConciliadoEn = recaudoConciliadoEn;
    this.modalidadRecaudo = modalidadRecaudo;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public Instant getDespachadoEn() {
    return despachadoEn;
  }

  public BigDecimal getComisionRecaudo() {
    return comisionRecaudo;
  }

  public Instant getRecaudoConciliadoEn() {
    return recaudoConciliadoEn;
  }

  public String getModalidadRecaudo() {
    return modalidadRecaudo;
  }
}
