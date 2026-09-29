package co.tecnosport.api.infrastructure.difusion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La fila de `publicacion_en_red`. Separada del agregado, con mapeador explícito. */
@Entity
@Table(name = "publicacion_en_red")
public class PublicacionEnRedJpaEntity {

  @Id private UUID id;

  /** Nulo cuando el producto se borró del catálogo: la constancia sobrevive al producto. */
  @Column(name = "producto_id")
  private UUID productoId;

  @Column(nullable = false)
  private String red;

  @Column(nullable = false)
  private String estado;

  @Column(name = "id_publicacion_externa")
  private String idPublicacionExterna;

  @Column(name = "pie_de_foto", nullable = false)
  private String pieDeFoto;

  @Column(name = "url_imagen", nullable = false)
  private String urlImagen;

  @Column(name = "solicitada_en", nullable = false)
  private Instant solicitadaEn;

  @Column(name = "publicada_en")
  private Instant publicadaEn;

  @Column(name = "detalle_del_fallo")
  private String detalleDelFallo;

  protected PublicacionEnRedJpaEntity() {}

  public PublicacionEnRedJpaEntity(
      UUID id,
      UUID productoId,
      String red,
      String estado,
      String idPublicacionExterna,
      String pieDeFoto,
      String urlImagen,
      Instant solicitadaEn,
      Instant publicadaEn,
      String detalleDelFallo) {
    this.id = id;
    this.productoId = productoId;
    this.red = red;
    this.estado = estado;
    this.idPublicacionExterna = idPublicacionExterna;
    this.pieDeFoto = pieDeFoto;
    this.urlImagen = urlImagen;
    this.solicitadaEn = solicitadaEn;
    this.publicadaEn = publicadaEn;
    this.detalleDelFallo = detalleDelFallo;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public String getRed() {
    return red;
  }

  public String getEstado() {
    return estado;
  }

  public String getIdPublicacionExterna() {
    return idPublicacionExterna;
  }

  public String getPieDeFoto() {
    return pieDeFoto;
  }

  public String getUrlImagen() {
    return urlImagen;
  }

  public Instant getSolicitadaEn() {
    return solicitadaEn;
  }

  public Instant getPublicadaEn() {
    return publicadaEn;
  }

  public String getDetalleDelFallo() {
    return detalleDelFallo;
  }
}
