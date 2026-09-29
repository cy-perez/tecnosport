package co.tecnosport.api.domain.difusion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La constancia de que un producto se difundió —o se intentó difundir— en una red social.
 *
 * <h2>Guarda el pie completo, y no una plantilla y unos datos</h2>
 *
 * <p>Podría parecer redundante: el nombre, el precio y el enlace salen del producto, así que el pie
 * se podría reconstruir. <b>No se puede, y esa es exactamente la razón de guardarlo.</b> El pie se
 * arma con el precio del día, con la descripción del día y con los hashtags que la categoría tenía
 * ese día, y además quien publica puede editarlo antes de enviarlo. Reconstruirlo un mes después
 * daría un texto distinto del que la gente leyó.
 *
 * <p>Y hay una consecuencia práctica: el pie guardado es lo único que sobrevive si el producto se
 * borra del catálogo. Por eso {@code productoId} puede quedar en nulo —la fila del producto se va,
 * la constancia se queda— y aun así sigue constando qué se publicó. La publicación en Instagram no
 * desaparece porque nosotros borremos una fila.
 *
 * <h2>No impone la idempotencia</h2>
 *
 * <p>Difundir el mismo producto dos veces es legítimo: se anuncia en septiembre y otra vez en
 * diciembre. Lo que hay que evitar es el doble clic, y eso es una ventana de tiempo, no una regla
 * del agregado — vive en el caso de uso, que es quien sabe qué hora es. Una restricción de unicidad
 * en la tabla habría prohibido el caso bueno para atajar el malo.
 */
public final class PublicacionEnRed {

  /**
   * Tope del pie. <b>Es un límite técnico, no el de la red.</b> Instagram admite 2.200 caracteres y
   * Facebook bastantes más; esos dos números son de Meta, cambian cuando Meta quiera y se irían con
   * ella el día que publiquemos en otro sitio, así que su lugar es la configuración del adaptador y
   * no este agregado — el mismo reparto que {@code PropiedadesSkydropx} hace con el rango del valor
   * declarado.
   *
   * <p>Lo que este número evita es otra cosa: que una fila crezca sin tope. Diez mil caracteres son
   * de sobra para cualquier pie que una persona escriba a mano.
   */
  public static final int MAXIMO_CARACTERES_PIE = 10_000;

  private final UUID id;
  private final UUID productoId;
  private final RedSocial red;
  private final String pieDeFoto;
  private final String urlImagen;
  private final Instant solicitadaEn;

  private EstadoPublicacion estado;
  private String idPublicacionExterna;
  private Instant publicadaEn;
  private String detalleDelFallo;

  public PublicacionEnRed(
      UUID id,
      UUID productoId,
      RedSocial red,
      String pieDeFoto,
      String urlImagen,
      EstadoPublicacion estado,
      String idPublicacionExterna,
      Instant solicitadaEn,
      Instant publicadaEn,
      String detalleDelFallo) {
    this.id = Objects.requireNonNull(id, "El id de la publicación no puede ser nulo.");
    this.productoId = productoId;
    this.red = Objects.requireNonNull(red, "La red social no puede ser nula.");
    this.pieDeFoto = exigirPie(pieDeFoto);
    this.urlImagen = exigirImagen(urlImagen);
    this.estado = Objects.requireNonNull(estado, "El estado de la publicación no puede ser nulo.");
    this.idPublicacionExterna = enBlancoEsNulo(idPublicacionExterna);
    this.solicitadaEn =
        Objects.requireNonNull(solicitadaEn, "La fecha de solicitud no puede ser nula.");
    this.publicadaEn = publicadaEn;
    this.detalleDelFallo = enBlancoEsNulo(detalleDelFallo);

    // Una fila PUBLICADA sin identificador externo no sirve para nada de lo que se guarda por: ni
    // se puede volver al post, ni se puede saber si ese post sigue vivo.
    if (this.estado == EstadoPublicacion.PUBLICADA && this.idPublicacionExterna == null) {
      throw new ExcepcionDeDominio(
          "Una publicación dada por publicada tiene que traer el identificador de la red.");
    }
  }

  /**
   * Se pidió la difusión. Nace {@code PENDIENTE} siempre: el resultado no se conoce hasta que la
   * red conteste, y nacer en cualquier otro estado sería adivinarlo.
   */
  public static PublicacionEnRed solicitar(
      UUID productoId, RedSocial red, String pieDeFoto, String urlImagen, Instant ahora) {
    if (productoId == null) {
      throw new ExcepcionDeDominio("No se difunde un producto sin decir cuál.");
    }
    return new PublicacionEnRed(
        GeneradorIdentificador.nuevo(),
        productoId,
        red,
        pieDeFoto,
        urlImagen,
        EstadoPublicacion.PENDIENTE,
        null,
        ahora,
        null,
        null);
  }

  /**
   * La red confirmó, y trae el identificador de la publicación.
   *
   * <p><b>Confirmar dos veces no es un error</b>, siempre que sea el mismo identificador: el
   * adaptador puede reintentar la consulta de estado y llegar dos veces a la misma respuesta. Lo
   * que sí es un error es que llegue un identificador distinto, porque entonces son dos
   * publicaciones y esta fila solo puede contar una.
   */
  public void confirmarPublicada(String idEnLaRed, Instant ahora) {
    String identificador = enBlancoEsNulo(idEnLaRed);
    if (identificador == null) {
      throw new ExcepcionDeDominio("La red tiene que devolver un identificador de la publicación.");
    }
    if (estado == EstadoPublicacion.PUBLICADA) {
      if (!identificador.equals(idPublicacionExterna)) {
        throw new ExcepcionDeDominio(
            "Esta difusión ya consta publicada con otro identificador: " + idPublicacionExterna);
      }
      return;
    }
    if (estado == EstadoPublicacion.FALLIDA) {
      throw new ExcepcionDeDominio("Una difusión que ya falló no puede darse por publicada.");
    }
    this.estado = EstadoPublicacion.PUBLICADA;
    this.idPublicacionExterna = identificador;
    this.publicadaEn = Objects.requireNonNull(ahora, "La fecha de publicación no puede ser nula.");
    this.detalleDelFallo = null;
  }

  /**
   * No salió. El motivo se guarda tal cual lo dio la red, porque es lo único que alguien va a tener
   * para decidir si reintenta o si hay que arreglar algo antes.
   */
  public void marcarFallida(String detalle) {
    if (estado == EstadoPublicacion.PUBLICADA) {
      throw new ExcepcionDeDominio(
          "Una difusión ya publicada no puede marcarse fallida: el post existe.");
    }
    this.estado = EstadoPublicacion.FALLIDA;
    this.detalleDelFallo = enBlancoEsNulo(detalle);
  }

  private static String exigirPie(String pie) {
    if (pie == null || pie.isBlank()) {
      throw new ExcepcionDeDominio("No se publica un producto sin pie de foto.");
    }
    String limpio = pie.strip();
    if (limpio.length() > MAXIMO_CARACTERES_PIE) {
      throw new ExcepcionDeDominio(
          "El pie de foto no puede pasar de " + MAXIMO_CARACTERES_PIE + " caracteres.");
    }
    return limpio;
  }

  private static String exigirImagen(String url) {
    if (url == null || url.isBlank()) {
      throw new ExcepcionDeDominio("No se publica un producto sin imagen.");
    }
    return url.strip();
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  public UUID id() {
    return id;
  }

  /** Vacío cuando el producto se borró del catálogo después de publicarse. */
  public Optional<UUID> productoId() {
    return Optional.ofNullable(productoId);
  }

  public RedSocial red() {
    return red;
  }

  public String pieDeFoto() {
    return pieDeFoto;
  }

  public String urlImagen() {
    return urlImagen;
  }

  public EstadoPublicacion estado() {
    return estado;
  }

  public Optional<String> idPublicacionExterna() {
    return Optional.ofNullable(idPublicacionExterna);
  }

  public Instant solicitadaEn() {
    return solicitadaEn;
  }

  public Optional<Instant> publicadaEn() {
    return Optional.ofNullable(publicadaEn);
  }

  public Optional<String> detalleDelFallo() {
    return Optional.ofNullable(detalleDelFallo);
  }
}
