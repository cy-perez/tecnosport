package co.tecnosport.api.infrastructure.garantia.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** El desenlace y su constancia son columnas de esta fila: son valores del agregado. */
@Entity
@Table(name = "reclamacion_garantia")
public class ReclamacionGarantiaJpaEntity {

  @Id private UUID id;

  @Column(name = "solicitud_id", nullable = false)
  private UUID solicitudId;

  @Column(name = "pedido_id", nullable = false)
  private UUID pedidoId;

  @Column(name = "variante_id", nullable = false)
  private UUID varianteId;

  @Column(name = "entregado_en", nullable = false)
  private Instant entregadoEn;

  @Column(name = "radicada_en", nullable = false)
  private Instant radicadaEn;

  @Column(name = "meses_de_termino")
  private Integer mesesDeTermino;

  @Column(name = "descripcion_del_fallo", nullable = false)
  private String descripcionDelFallo;

  @Column(nullable = false)
  private String estado;

  @Column private String desenlace;

  @Column(name = "resuelta_en")
  private Instant resueltaEn;

  @Column(name = "resuelta_por")
  private String resueltaPor;

  @Column(name = "reintegro_id")
  private UUID reintegroId;

  protected ReclamacionGarantiaJpaEntity() {}

  public ReclamacionGarantiaJpaEntity(
      UUID id,
      UUID solicitudId,
      UUID pedidoId,
      UUID varianteId,
      Instant entregadoEn,
      Instant radicadaEn,
      Integer mesesDeTermino,
      String descripcionDelFallo,
      String estado,
      String desenlace,
      Instant resueltaEn,
      String resueltaPor,
      UUID reintegroId) {
    this.id = id;
    this.solicitudId = solicitudId;
    this.pedidoId = pedidoId;
    this.varianteId = varianteId;
    this.entregadoEn = entregadoEn;
    this.radicadaEn = radicadaEn;
    this.mesesDeTermino = mesesDeTermino;
    this.descripcionDelFallo = descripcionDelFallo;
    this.estado = estado;
    this.desenlace = desenlace;
    this.resueltaEn = resueltaEn;
    this.resueltaPor = resueltaPor;
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

  public UUID getVarianteId() {
    return varianteId;
  }

  public Instant getEntregadoEn() {
    return entregadoEn;
  }

  public Instant getRadicadaEn() {
    return radicadaEn;
  }

  public Integer getMesesDeTermino() {
    return mesesDeTermino;
  }

  public String getDescripcionDelFallo() {
    return descripcionDelFallo;
  }

  public String getEstado() {
    return estado;
  }

  public String getDesenlace() {
    return desenlace;
  }

  public Instant getResueltaEn() {
    return resueltaEn;
  }

  public String getResueltaPor() {
    return resueltaPor;
  }

  public UUID getReintegroId() {
    return reintegroId;
  }
}
