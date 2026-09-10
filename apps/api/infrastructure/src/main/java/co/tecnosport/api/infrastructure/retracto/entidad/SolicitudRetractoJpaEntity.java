package co.tecnosport.api.infrastructure.retracto.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code reintegroId} apunta a la constancia del dinero devuelto, que desde V23 vive en su propia
 * tabla: es una sola para los cinco caminos que devuelven dinero, y el retracto es uno de ellos.
 */
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

  @Column(name = "reintegro_id")
  private UUID reintegroId;

  /**
   * Guardado como {@code String} y no como {@code @Enumerated}: la columna lleva su propio {@code
   * check} en V27, y un enum de JPA por ordinal es la forma clásica de que reordenar el enum cambie
   * el significado de las filas ya escritas.
   */
  @Column(name = "medio_preferido")
  private String medioPreferido;

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
      UUID reintegroId,
      String medioPreferido) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.radicadaEn = radicadaEn;
    this.radicadaPor = radicadaPor;
    this.motivo = motivo;
    this.verdictoPlazo = verdictoPlazo;
    this.estado = estado;
    this.productoRecibidoEn = productoRecibidoEn;
    this.reintegroId = reintegroId;
    this.medioPreferido = medioPreferido;
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

  public UUID getReintegroId() {
    return reintegroId;
  }

  public String getMedioPreferido() {
    return medioPreferido;
  }
}
