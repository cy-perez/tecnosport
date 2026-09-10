package co.tecnosport.api.infrastructure.reversion.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La gestión y el desenlace son columnas de esta fila: son valores del agregado. */
@Entity
@Table(name = "solicitud_reversion")
public class SolicitudReversionJpaEntity {

  @Id private UUID id;

  @Column(name = "solicitud_id", nullable = false)
  private UUID solicitudId;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(nullable = false)
  private String causal;

  @Column(name = "fecha_del_hecho", nullable = false)
  private Instant fechaDelHecho;

  @Column(name = "radicada_en", nullable = false)
  private Instant radicadaEn;

  @Column(name = "verdicto_plazo", nullable = false)
  private String verdictoPlazo;

  @Column(nullable = false)
  private String estado;

  @Column(name = "gestionada_en")
  private Instant gestionadaEn;

  @Column(name = "gestionada_por")
  private String gestionadaPor;

  @Column private String gestion;

  @Column private String desenlace;

  @Column(name = "resuelta_en")
  private Instant resueltaEn;

  @Column(name = "reintegro_id")
  private UUID reintegroId;

  protected SolicitudReversionJpaEntity() {}

  public SolicitudReversionJpaEntity(
      UUID id,
      UUID solicitudId,
      UUID pedidoId,
      String causal,
      Instant fechaDelHecho,
      Instant radicadaEn,
      String verdictoPlazo,
      String estado,
      Instant gestionadaEn,
      String gestionadaPor,
      String gestion,
      String desenlace,
      Instant resueltaEn,
      UUID reintegroId) {
    this.id = id;
    this.solicitudId = solicitudId;
    this.pedidoId = pedidoId;
    this.causal = causal;
    this.fechaDelHecho = fechaDelHecho;
    this.radicadaEn = radicadaEn;
    this.verdictoPlazo = verdictoPlazo;
    this.estado = estado;
    this.gestionadaEn = gestionadaEn;
    this.gestionadaPor = gestionadaPor;
    this.gestion = gestion;
    this.desenlace = desenlace;
    this.resueltaEn = resueltaEn;
    this.reintegroId = reintegroId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSolicitudId() {
    return solicitudId;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getCausal() {
    return causal;
  }

  public Instant getFechaDelHecho() {
    return fechaDelHecho;
  }

  public Instant getRadicadaEn() {
    return radicadaEn;
  }

  public String getVerdictoPlazo() {
    return verdictoPlazo;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getGestionadaEn() {
    return gestionadaEn;
  }

  public String getGestionadaPor() {
    return gestionadaPor;
  }

  public String getGestion() {
    return gestion;
  }

  public String getDesenlace() {
    return desenlace;
  }

  public Instant getResueltaEn() {
    return resueltaEn;
  }

  public UUID getReintegroId() {
    return reintegroId;
  }
}
