package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Una imagen de producto o variante, publicada en una o varias resoluciones. {@code altEs}/{@code
 * altEn} son obligatorios en PRINCIPAL y GALERIA; en ROTACION son decorativos y pueden ir vacíos,
 * ver docs/02-modelo-datos.md.
 *
 * <p><strong>La URL, el ancho y el peso no son campos: salen de la variante mayor.</strong> Antes
 * eran un campo cada uno y convivían con {@code urlWebp}, que guardaba la URL del mismo objeto —y
 * desde ADR-0056, la de un AVIF—. Derivarlos deja una sola fuente de verdad dentro del agregado: no
 * hay dos sitios donde escribir la URL de la misma imagen, así que tampoco hay una invariante que
 * vigile que coincidan. La columna {@code url} de la base de datos se queda, porque la leen el
 * {@code og:image}, la línea del carrito y el panel, y sale de aquí al guardar.
 *
 * <p><strong>{@code urlVistaPrevia} es opcional y tiene un solo destinatario</strong>: el
 * previsualizador de WhatsApp o de Facebook, que no negocia formatos y con AVIF no muestra nada. Es
 * un JPEG del ancho base. No es un respaldo del {@code <img>} del navegador —el sitio no envuelve
 * las imágenes en {@code <picture>}, ver ADR-0056— y llamarla "respaldo" invitaría a usarla para
 * eso.
 */
public final class ImagenProducto {

  private final UUID id;
  private final TipoImagen tipo;
  private final int orden;
  private final List<VarianteDeImagen> variantes;
  private final String urlVistaPrevia;
  private final int alto;
  private final HashContenido hash;
  private final String altEs;
  private final String altEn;

  public ImagenProducto(
      UUID id,
      TipoImagen tipo,
      int orden,
      List<VarianteDeImagen> variantes,
      String urlVistaPrevia,
      int alto,
      HashContenido hash,
      String altEs,
      String altEn) {
    this.id = Objects.requireNonNull(id, "El id de la imagen no puede ser nulo.");
    this.tipo = Objects.requireNonNull(tipo, "El tipo de la imagen no puede ser nulo.");
    if (orden < 0) {
      throw new ImagenProductoInvalidaException("El orden de la imagen no puede ser negativo.");
    }
    this.orden = orden;
    this.variantes = ordenadasYSinRepetir(variantes);
    this.urlVistaPrevia =
        urlVistaPrevia == null || urlVistaPrevia.isBlank() ? null : urlVistaPrevia.trim();
    if (alto <= 0) {
      throw new ImagenProductoInvalidaException("El alto de la imagen debe ser positivo.");
    }
    this.alto = alto;
    this.hash = Objects.requireNonNull(hash, "El hash de la imagen no puede ser nulo.");

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
      List<VarianteDeImagen> variantes,
      String urlVistaPrevia,
      int alto,
      HashContenido hash,
      String altEs,
      String altEn) {
    return new ImagenProducto(
        GeneradorIdentificador.nuevo(),
        tipo,
        orden,
        variantes,
        urlVistaPrevia,
        alto,
        hash,
        altEs,
        altEn);
  }

  /**
   * La misma imagen en otra posición de la galería.
   *
   * <p>Visible solo en el paquete, y a propósito: el orden de una imagen no es una propiedad suya
   * que se pueda cambiar suelta, es el sitio que ocupa dentro de una galería. Quien decide eso es
   * {@link Producto#reordenarGaleria}, que puede comprobar que el resultado siga teniendo sentido
   * —ninguna repetida, ninguna perdida—; desde fuera del agregado, cambiar el orden de una sola
   * imagen solo puede dejar dos en la misma posición.
   */
  ImagenProducto conOrden(int nuevoOrden) {
    return new ImagenProducto(
        id, tipo, nuevoOrden, variantes, urlVistaPrevia, alto, hash, altEs, altEn);
  }

  /**
   * Las variantes de menor a mayor ancho, que es el orden en que las quiere un {@code srcset}.
   *
   * <p>Se rechazan dos anchos iguales en vez de quedarse con uno: si llegan dos, quien las armó
   * cree que publicó dos cosas distintas y una de las dos se estaría perdiendo en silencio.
   */
  private static List<VarianteDeImagen> ordenadasYSinRepetir(List<VarianteDeImagen> variantes) {
    if (variantes == null || variantes.isEmpty()) {
      throw new ImagenProductoInvalidaException(
          "Una imagen necesita al menos una variante publicada.");
    }
    if (variantes.stream().anyMatch(Objects::isNull)) {
      throw new ImagenProductoInvalidaException("Una variante de la imagen es nula.");
    }
    List<VarianteDeImagen> ordenadas = new ArrayList<>(variantes);
    ordenadas.sort(Comparator.comparingInt(VarianteDeImagen::ancho));
    for (int i = 1; i < ordenadas.size(); i++) {
      if (ordenadas.get(i).ancho() == ordenadas.get(i - 1).ancho()) {
        throw new ImagenProductoInvalidaException(
            "Hay dos variantes de la imagen con el mismo ancho ("
                + ordenadas.get(i).ancho()
                + " px).");
      }
    }
    return List.copyOf(ordenadas);
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

  /** Las variantes publicadas, de menor a mayor ancho. Nunca está vacía. */
  public List<VarianteDeImagen> variantes() {
    return variantes;
  }

  /**
   * La variante mayor: la que se sirve cuando el navegador no elige, y de la que salen url, ancho y
   * bytes.
   */
  public VarianteDeImagen base() {
    return variantes.get(variantes.size() - 1);
  }

  public String url() {
    return base().url();
  }

  public int ancho() {
    return base().ancho();
  }

  public long bytes() {
    return base().bytes();
  }

  /** El JPEG para los previsualizadores de enlaces. Vacío mientras no se haya subido. */
  public Optional<String> urlVistaPrevia() {
    return Optional.ofNullable(urlVistaPrevia);
  }

  public int alto() {
    return alto;
  }

  public HashContenido hash() {
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
