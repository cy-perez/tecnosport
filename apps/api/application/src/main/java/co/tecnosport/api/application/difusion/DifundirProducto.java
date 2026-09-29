package co.tecnosport.api.application.difusion;

import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.difusion.ProductoNoDifundibleException;
import co.tecnosport.api.domain.difusion.PublicacionEnRed;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Difunde un producto en las redes que se le pidan.
 *
 * <h2>Cada red va por su cuenta</h2>
 *
 * <p>Pedir Facebook e Instagram a la vez son dos publicaciones independientes, y <b>que una falle
 * no cancela la otra</b>. Es lo contrario de lo que haría una transacción, y a propósito: cuando
 * Instagram rechaza la imagen, lo último que se quiere es borrar el post que Facebook ya publicó —
 * nada de lo que hagamos aquí lo despublica, así que fingir que no pasó solo dejaría la base
 * mintiendo. Cada red deja su fila con su resultado y el panel las enseña las dos.
 *
 * <h2>El pie se puede traer escrito</h2>
 *
 * <p>Quien publica ve el texto propuesto en el panel y lo puede cambiar antes de enviar. Si viene
 * en el comando se usa tal cual; si no, lo arma {@link ArmadorDePieDeFoto}. Lo que se guarda es
 * siempre el que se mandó, no la plantilla que lo generó.
 */
public final class DifundirProducto {

  /**
   * Cuánto tiene que pasar para que la misma difusión no se considere un doble clic.
   *
   * <p>Treinta segundos es lo que tarda alguien en darse cuenta de que no pasó nada y volver a
   * pulsar. Más largo empezaría a estorbar al caso legítimo —corregir el pie y volver a publicar— y
   * más corto no atajaría nada, porque la publicación en Instagram son dos viajes y puede tardar
   * varios segundos en contestar.
   */
  private static final Duration VENTANA_ANTI_DOBLE_CLIC = Duration.ofSeconds(30);

  private final RepositorioProductos repositorioProductos;
  private final RepositorioPublicaciones repositorioPublicaciones;
  private final PublicadorEnRedSocial publicador;
  private final ArmadorDePieDeFoto armador;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;

  public DifundirProducto(
      RepositorioProductos repositorioProductos,
      RepositorioPublicaciones repositorioPublicaciones,
      PublicadorEnRedSocial publicador,
      ArmadorDePieDeFoto armador,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    this.repositorioProductos =
        Objects.requireNonNull(repositorioProductos, "El repositorio de productos es obligatorio.");
    this.repositorioPublicaciones =
        Objects.requireNonNull(
            repositorioPublicaciones, "El repositorio de publicaciones es obligatorio.");
    this.publicador = Objects.requireNonNull(publicador, "El publicador es obligatorio.");
    this.armador = Objects.requireNonNull(armador, "El armador del pie es obligatorio.");
    this.enTransaccionPropia =
        Objects.requireNonNull(enTransaccionPropia, "La transacción propia es obligatoria.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj es obligatorio.");
  }

  public List<PublicacionEnRed> ejecutar(DifundirProductoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    String urlImagen = exigirImagenPublicable(producto);
    exigirPublicadoEnLaTienda(producto);

    List<PublicacionEnRed> resultados = new ArrayList<>();
    for (RedSocial red : sinRepetir(comando.redes())) {
      resultados.add(difundirEn(producto, red, urlImagen, comando.pieDeFoto()));
    }
    return resultados;
  }

  /**
   * El pie que se propondría, sin publicar nada.
   *
   * <p>Lo arma el servidor y no el navegador porque lleva el precio, y el precio lo decide el
   * servidor (regla dura #7). Un pie armado en el cliente sería el cliente eligiendo qué se
   * anuncia.
   *
   * <p>Pasa por las mismas guardas que difundir -- que el producto exista, esté publicado y tenga
   * imagen -- para que el panel se entere de que algo falta <b>antes</b> de que alguien pulse el
   * botón, y no con un error después.
   */
  public String proponerPie(UUID productoId, RedSocial red) {
    Producto producto =
        repositorioProductos
            .buscarPorId(productoId)
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));
    exigirImagenPublicable(producto);
    exigirPublicadoEnLaTienda(producto);
    return armador.armar(producto, red);
  }

  private PublicacionEnRed difundirEn(
      Producto producto, RedSocial red, String urlImagen, String pieEscritoAMano) {
    if (repositorioPublicaciones.hayUnaReciente(
        producto.id(), red, reloj.ahora().minus(VENTANA_ANTI_DOBLE_CLIC))) {
      throw new DifusionRepetidaException(producto.id(), red);
    }

    String pie =
        pieEscritoAMano == null || pieEscritoAMano.isBlank()
            ? armador.armar(producto, red)
            : pieEscritoAMano;

    // Se guarda PENDIENTE **antes** de llamar a la red, y **en su propia transacción**. Las dos
    // cosas hacen falta: guardarlo después dejaría un post publicado sin rastro en la base si el
    // proceso se cae en mitad del viaje, y guardarlo dentro de la transacción de quien llama sería
    // guardarlo sin confirmar -- la fila no existiría todavía cuando Meta ya publicó.
    //
    // Es la misma forma que `EmitirGuiaDePedido` (adr/0033) y por la misma razón: en la mitad hay
    // un tercero que hace algo que ninguna transacción de base de datos revierte. Un post de
    // Instagram no se deshace con un rollback.
    PublicacionEnRed publicacion =
        enTransaccionPropia.ejecutar(
            () -> {
              PublicacionEnRed nueva =
                  PublicacionEnRed.solicitar(producto.id(), red, pie, urlImagen, reloj.ahora());
              repositorioPublicaciones.guardar(nueva);
              return nueva;
            });

    ResultadoPublicacion resultado = publicador.publicar(red, urlImagen, pie);
    if (resultado.salioBien()) {
      publicacion.confirmarPublicada(resultado.idEnLaRed(), reloj.ahora());
    } else {
      publicacion.marcarFallida(resultado.motivoDelFallo());
    }
    repositorioPublicaciones.guardar(publicacion);
    return publicacion;
  }

  /**
   * La URL que se le va a dar a Meta para que descargue la imagen, que <b>no es la que enseña el
   * sitio</b>: la del sitio es AVIF y Meta no lo entiende. La vista previa en JPEG existe desde
   * antes de todo esto, para los previsualizadores de enlaces de WhatsApp y Facebook, y sirve aquí
   * tal cual — cuadrada y de 1200 px como mucho, dentro de lo que Meta admite.
   */
  private static String exigirImagenPublicable(Producto producto) {
    ImagenProducto principal =
        producto
            .imagenPrincipal()
            .orElseThrow(() -> ProductoNoDifundibleException.sinImagen(producto.id()));
    return principal
        .urlVistaPrevia()
        .orElseThrow(() -> ProductoNoDifundibleException.sinVistaPrevia(producto.id()));
  }

  /**
   * Un borrador no se anuncia. El pie lleva el enlace a la ficha y la ficha de un borrador responde
   * 404: se estaría pagando alcance para mandar gente a una página que no existe.
   */
  private static void exigirPublicadoEnLaTienda(Producto producto) {
    if (producto.estado() != EstadoProducto.PUBLICADO) {
      throw ProductoNoDifundibleException.noPublicado(producto.id());
    }
  }

  /** Pedir dos veces la misma red en el mismo comando es un descuido, no una intención. */
  private static Set<RedSocial> sinRepetir(List<RedSocial> redes) {
    if (redes == null || redes.isEmpty()) {
      throw new IllegalArgumentException("Hay que decir en qué red se difunde.");
    }
    return new LinkedHashSet<>(redes);
  }
}
