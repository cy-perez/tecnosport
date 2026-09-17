package co.tecnosport.api.infrastructure.envio.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emision_de_guia")
public class EmisionDeGuiaJpaEntity {

  @Id private UUID id;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  /** El nombre visible. La respuesta del envío solo trae el código de la plataforma. */
  @Column(nullable = false)
  private String transportadora;

  @Column(name = "id_tarifa", nullable = false)
  private String idTarifa;

  @Column(nullable = false)
  private String estado;

  @Column private String detalle;

  @Column(name = "solicitada_en", nullable = false)
  private Instant solicitadaEn;

  @Column(name = "resuelta_en")
  private Instant resueltaEn;

  protected EmisionDeGuiaJpaEntity() {}

  public EmisionDeGuiaJpaEntity(
      UUID id,
      UUID pedidoId,
      String transportadora,
      String idTarifa,
      String estado,
      String detalle,
      Instant solicitadaEn,
      Instant resueltaEn) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.transportadora = transportadora;
    this.idTarifa = idTarifa;
    this.estado = estado;
    this.detalle = detalle;
    this.solicitadaEn = solicitadaEn;
    this.resueltaEn = resueltaEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getTransportadora() {
    return transportadora;
  }

  public String getIdTarifa() {
    return idTarifa;
  }

  public String getEstado() {
    return estado;
  }

  public String getDetalle() {
    return detalle;
  }

  public Instant getSolicitadaEn() {
    return solicitadaEn;
  }

  public Instant getResueltaEn() {
    return resueltaEn;
  }
}
