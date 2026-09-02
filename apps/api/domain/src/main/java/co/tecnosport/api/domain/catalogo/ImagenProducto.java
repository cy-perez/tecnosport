package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.Objects;
import java.util.UUID;

/**
 * Una imagen de producto o variante. {@code altEs}/{@code altEn} son obligatorios en PRINCIPAL y
 * GALERIA; en ROTACION son decorativos y pueden ir vacíos, ver docs/02-modelo-datos.md.
 */
public final class ImagenProducto {

  private final UUID id;
  private final TipoImagen tipo;
  private final int orden;
  private final String url;
  private final String urlWebp;
  private final int ancho;
  private final int alto;
  private final long bytes;
  private final String hash;
  private final String altEs;
  private final String altEn;

  public ImagenProducto(
      UUID id,
      TipoImagen tipo,
      int orden,
      String url,
      String urlWebp,
      int ancho,
      int alto,
      long bytes,
      String hash,
      String altEs,
      String altEn) {
    this.id = Objects.requireNonNull(id, "El id de la imagen no puede ser nulo.");
    this.tipo = Objects.requireNonNull(tipo, "El tipo de la imagen no puede ser nulo.");
    if (orden < 0) {
      throw new ImagenProductoInvalidaException("El orden de la imagen no puede ser negativo.");
    }
    this.orden = orden;
    this.url = requerido(url, "La URL de la imagen no puede estar vacía.");
    this.urlWebp = requerido(urlWebp, "La URL webp de la imagen no puede estar vacía.");
    if (ancho <= 0 || alto <= 0) {
      throw new ImagenProductoInvalidaException(
          "Las dimensiones de la imagen deben ser positivas.");
    }
    this.ancho = ancho;
    this.alto = alto;
    if (bytes <= 0) {
      throw new ImagenProductoInvalidaException(
          "El tamaño en bytes de la imagen debe ser positivo.");
    }
    this.bytes = bytes;
    this.hash = requerido(hash, "El hash de la imagen no puede estar vacío.");

    if (tipo == TipoImagen.PRINCIPAL || tipo == TipoImagen.GALERIA) {
      this.altEs =
          requerido(altEs, "El texto alternativo en español es obligatorio para " + tipo + ".");
      this.altEn =
          requerido(altEn, "El texto alternativo en inglés es obligatorio para " + tipo + ".");
    } else {
      this.altEs = altEs == null ? "" : altEs.trim();
      this.altEn = altEn == null ? "" : altEn.trim();
    }
  }

  public static ImagenProducto crear(
      TipoImagen tipo,
      int orden,
      String url,
      String urlWebp,
      int ancho,
      int alto,
      long bytes,
      String hash,
      String altEs,
      String altEn) {
    return new ImagenProducto(
        GeneradorIdentificador.nuevo(),
        tipo,
        orden,
        url,
        urlWebp,
        ancho,
        alto,
        bytes,
        hash,
        altEs,
        altEn);
  }

  private static String requerido(String valor, String mensaje) {
    if (valor == null || valor.isBlank()) {
      throw new ImagenProductoInvalidaException(mensaje);
    }
    return valor.trim();
  }

  public UUID id() {
    return id;
  }

  public TipoImagen tipo() {
    return tipo;
  }

  public int orden() {
    return orden;
  }

  public String url() {
    return url;
  }

  public String urlWebp() {
    return urlWebp;
  }

  public int ancho() {
    return ancho;
  }

  public int alto() {
    return alto;
  }

  public long bytes() {
    return bytes;
  }

  public String hash() {
    return hash;
  }

  public String altEs() {
    return altEs;
  }

  public String altEn() {
    return altEn;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ImagenProducto otra && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
