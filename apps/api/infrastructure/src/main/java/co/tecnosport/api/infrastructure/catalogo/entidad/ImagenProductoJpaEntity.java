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

  @Column(nullable = false)
  private String tipo;

  @Column(nullable = false)
  private int orden;

  @Column(nullable = false)
  private String url;

  /** Nulable: una imagen sin vista previa se ve bien en el sitio y no se ve en WhatsApp. */
  @Column(name = "url_vista_previa")
  private String urlVistaPrevia;

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

  /**
   * Solo una PRINCIPAL la lleva en verdadero: la que vale para todos y aun asi entra en la ficha.
   */
  @Column(name = "en_la_ficha", nullable = false)
  private boolean enLaFicha;

  /** Los ids de las variantes sin foto propia que la foto retrata, separados por coma; o nula. */
  @Column(name = "muestra_variantes")
  private String muestraVariantes;

  protected ImagenProductoJpaEntity() {}

  public ImagenProductoJpaEntity(
      UUID id,
      UUID productoId,
      UUID varianteId,
      String tipo,
      int orden,
      String url,
      String urlVistaPrevia,
      int ancho,
      int alto,
      long bytes,
      String hash,
      String altEs,
      String altEn,
      Instant creadaEn) {
    this(
        id,
        productoId,
        varianteId,
        tipo,
        orden,
        url,
        urlVistaPrevia,
        ancho,
        alto,
        bytes,
        hash,
        altEs,
        altEn,
        creadaEn,
        false,
        null);
  }

  public ImagenProductoJpaEntity(
      UUID id,
      UUID productoId,
      UUID varianteId,
      String tipo,
      int orden,
      String url,
      String urlVistaPrevia,
      int ancho,
      int alto,
      long bytes,
      String hash,
      String altEs,
      String altEn,
      Instant creadaEn,
      boolean enLaFicha,
      String muestraVariantes) {
    this.enLaFicha = enLaFicha;
    this.muestraVariantes = muestraVariantes;
    this.id = id;
    this.productoId = productoId;
    this.varianteId = varianteId;
    this.tipo = tipo;
    this.orden = orden;
    this.url = url;
    this.urlVistaPrevia = urlVistaPrevia;
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

  public String getTipo() {
    return tipo;
  }

  /**
   * El unico campo que se puede cambiar despues de construida, y solo por el reordenamiento de la
   * galeria. La entidad no tiene setters a proposito: una fila de imagen se escribe entera al
   * crearse. El orden es la excepcion porque es lo unico que cambia sin que cambie la imagen.
   */
  public void cambiarOrden(int nuevoOrden) {
    this.orden = nuevoOrden;
  }

  public void cambiarVariante(UUID nuevaVarianteId) {
    this.varianteId = nuevaVarianteId;
  }

  public int getOrden() {
    return orden;
  }

  public String getUrl() {
    return url;
  }

  public String getUrlVistaPrevia() {
    return urlVistaPrevia;
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

  public boolean isEnLaFicha() {
    return enLaFicha;
  }

  public String getMuestraVariantes() {
    return muestraVariantes;
  }
}
