package co.tecnosport.api.infrastructure.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imagen_producto")
public class ImagenProductoJpaEntity {

  @Id private UUID id;

  @Column(name = "producto_id", nullable = false)
  private UUID productoId;

  @Column(name = "variante_id")
  private UUID varianteId;

  @Column(name = "set_rotacion_id")
  private UUID setRotacionId;

  @Column(nullable = false)
  private String tipo;

  @Column(nullable = false)
  private int orden;

  @Column(nullable = false)
  private String url;

  @Column(name = "url_webp", nullable = false)
  private String urlWebp;

  @Column(nullable = false)
  private int ancho;

  @Column(nullable = false)
  private int alto;

  @Column(nullable = false)
  private long bytes;

  @Column(nullable = false)
  private String hash;

  @Column(name = "alt_es", nullable = false)
  private String altEs;

  @Column(name = "alt_en", nullable = false)
  private String altEn;

  @Column(name = "creada_en", nullable = false)
  private Instant creadaEn;

  protected ImagenProductoJpaEntity() {}

  public ImagenProductoJpaEntity(
      UUID id,
      UUID productoId,
      UUID varianteId,
      UUID setRotacionId,
      String tipo,
      int orden,
      String url,
      String urlWebp,
      int ancho,
      int alto,
      long bytes,
      String hash,
      String altEs,
      String altEn,
      Instant creadaEn) {
    this.id = id;
    this.productoId = productoId;
    this.varianteId = varianteId;
    this.setRotacionId = setRotacionId;
    this.tipo = tipo;
    this.orden = orden;
    this.url = url;
    this.urlWebp = urlWebp;
    this.ancho = ancho;
    this.alto = alto;
    this.bytes = bytes;
    this.hash = hash;
    this.altEs = altEs;
    this.altEn = altEn;
    this.creadaEn = creadaEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductoId() {
    return productoId;
  }

  public UUID getVarianteId() {
    return varianteId;
  }

  public UUID getSetRotacionId() {
    return setRotacionId;
  }

  public String getTipo() {
    return tipo;
  }

  public int getOrden() {
    return orden;
  }

  public String getUrl() {
    return url;
  }

  public String getUrlWebp() {
    return urlWebp;
  }

  public int getAncho() {
    return ancho;
  }

  public int getAlto() {
    return alto;
  }

  public long getBytes() {
    return bytes;
  }

  public String getHash() {
    return hash;
  }

  public String getAltEs() {
    return altEs;
  }

  public String getAltEn() {
    return altEn;
  }

  public Instant getCreadaEn() {
    return creadaEn;
  }
}
