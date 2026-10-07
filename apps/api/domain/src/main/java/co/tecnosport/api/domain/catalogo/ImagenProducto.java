package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.HashContenido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
  private final UUID varianteId;

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
    this(id, tipo, orden, variantes, urlVistaPrevia, alto, hash, altEs, altEn, null);
  }

  /**
   * @param varianteId la variante a la que pertenece la foto —el tono que muestra— o nulo cuando es
   *     del producto entero. Es lo que la ficha usa para cambiar de foto al elegir un color, y lo
   *     que la aprobación de un borrador de proveedor asigna por foto.
   */
  public ImagenProducto(
      UUID id,
      TipoImagen tipo,
      int orden,
      List<VarianteDeImagen> variantes,
      String urlVistaPrevia,
      int alto,
      HashContenido hash,
      String altEs,
      String altEn,
      UUID varianteId) {
    this.varianteId = varianteId;
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
  /** Una imagen de una variante concreta: la foto del tono. */
  public static ImagenProducto crearDeVariante(
      TipoImagen tipo,
      int orden,
      List<VarianteDeImagen> variantes,
      String urlVistaPrevia,
      int alto,
      HashContenido hash,
      String altEs,
      String altEn,
      UUID varianteId) {
    return new ImagenProducto(
        GeneradorIdentificador.nuevo(),
        tipo,
        orden,
        variantes,
        urlVistaPrevia,
        alto,
        hash,
        altEs,
        altEn,
        varianteId);
  }

  /** La misma foto colgada de otra variante —otro tono—, o de ninguna. */
  ImagenProducto conVariante(UUID nuevaVarianteId) {
    return new ImagenProducto(
        id, tipo, orden, variantes, urlVistaPrevia, alto, hash, altEs, altEn, nuevaVarianteId);
  }

  /**
   * Esta foto de la galería convertida en la principal: otro id —es otra fila— y orden 0. Los
   * objetos del bucket son los mismos.
   *
   * <p><b>Conserva su variante, y hasta el 7 de octubre de 2026 la perdía.</b> La soltaba porque la
   * principal era, por definición, la del producto entero. Desde que el color de la principal
   * decide si la ficha la enseña, soltarla tiene un efecto que nadie pediría: ascender la foto de
   * un tono la convierte en genérica y <b>desaparece de la galería</b> — quien la asciende ve una
   * foto menos en la ficha, sin que nada lo explique.
   */
  ImagenProducto comoPrincipal() {
    return new ImagenProducto(
        GeneradorIdentificador.nuevo(),
        TipoImagen.PRINCIPAL,
        0,
        variantes,
        urlVistaPrevia,
        alto,
        hash,
        altEs,
        altEn,
        varianteId);
  }

  /**
   * La principal convertida en una foto de la galería, en el puesto que se le indique. Conserva su
   * variante por lo mismo que {@link #comoPrincipal()}: intercambiar las dos no puede perder de qué
   * tono es cada foto.
   */
  ImagenProducto comoGaleria(int nuevoOrden) {
    return new ImagenProducto(
        GeneradorIdentificador.nuevo(),
        TipoImagen.GALERIA,
        nuevoOrden,
        variantes,
        urlVistaPrevia,
        alto,
        hash,
        altEs,
        altEn,
        varianteId);
  }

  ImagenProducto conOrden(int nuevoOrden) {
    return new ImagenProducto(
        id, tipo, nuevoOrden, variantes, urlVistaPrevia, alto, hash, altEs, altEn, varianteId);
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

  /**
   * La URL que se le puede dar a un tercero que descarga la imagen y no negocia formatos —Meta al
   * publicar en Facebook o Instagram, los previsualizadores de enlaces—. Vacía cuando no hay
   * ninguna que ese tercero sepa leer.
   *
   * <p><b>La vista previa si la hay y, si no, la propia imagen cuando su formato lo permite.</b>
   * Ese segundo caso es el que faltaba, y costó que la difusión en redes estuviera rota del todo
   * para una parte entera del catálogo: {@code DifundirProducto} exigía la vista previa, y las
   * fotos que entran aprobando un borrador de proveedor se publican tal como llegaron —JPEG— y
   * nunca tuvieron una. O sea que exigirla rechazaba, por no tener una conversión a JPEG, fotos que
   * <b>ya son</b> JPEG.
   *
   * <p>La vista previa manda cuando existe porque es cuadrada y de 1200 px como mucho, que es lo
   * que estos terceros quieren; la imagen del sitio puede ser de cualquier tamaño.
   *
   * <p>El formato se deduce de la extensión de la URL, que es lo único que hay: el agregado no
   * guarda el tipo de contenido de cada variante. Es suficiente porque las URL las escribe el
   * almacén a partir de una key que él mismo nombra con la extensión del tipo ({@code
   * AlmacenDeImagenes}). Una URL sin extensión reconocible se trata como no publicable, que es el
   * lado seguro: vale más no publicar que publicar un post sin foto.
   */
  public Optional<String> urlParaTercerosQueNoNegocianFormato() {
    if (urlVistaPrevia != null) {
      return Optional.of(urlVistaPrevia);
    }
    return Optional.ofNullable(url()).filter(ImagenProducto::esFormatoUniversal);
  }

  /**
   * Los tres formatos que entiende cualquiera. Quedan fuera AVIF y WebP, que son justo los que el
   * sitio sirve desde {@code ADR-0056} y los que no muestran nada en un previsualizador.
   */
  private static boolean esFormatoUniversal(String url) {
    String sinConsulta = url.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
    return sinConsulta.endsWith(".jpg")
        || sinConsulta.endsWith(".jpeg")
        || sinConsulta.endsWith(".png");
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

  /** Vacío cuando la foto es del producto entero y no de un tono. */
  public Optional<UUID> varianteId() {
    return Optional.ofNullable(varianteId);
  }
}
