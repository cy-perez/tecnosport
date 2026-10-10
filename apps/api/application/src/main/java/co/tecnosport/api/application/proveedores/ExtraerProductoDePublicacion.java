package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PatronDePrecio;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.Respaldo;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Le pide al extractor que lea una publicación, y no se cree todo lo que dice.
 *
 * <h2>Las salvaguardas fuera del modelo</h2>
 *
 * <p>El precio también se lee con {@link PatronDePrecio}. Si los dos coinciden, bien; si el
 * extractor dio otro, el borrador queda con {@code PRECIO_INCONSISTENTE} y <b>se sigue con el del
 * texto</b>, que es el que el proveedor escribió; si el extractor no dio ninguno, también se sigue
 * con el del texto y se alerta igual. Sin ninguno de los dos no hay con qué calcular un margen y la
 * alerta es {@code SIN_PRECIO}. Título vacío, tipo desconocido, confianza por debajo del umbral y
 * publicación sin fotos con archivo alertan también. Ninguna alerta detiene nada: el borrador se
 * crea igual y lo mira una persona.
 *
 * <p>Tampoco se cree el «sirve hasta» que el texto no escribe, y una réplica se reconoce por el
 * «1.1» del texto aunque el extractor no la marque ({@link ProductoExtraido#contrastadoCon}).
 *
 * <h2>Varios productos en un mensaje</h2>
 *
 * <p>Cada producto se contrasta con <b>el precio de su misma posición</b> en el texto, y solo si el
 * texto trae exactamente un precio por producto: con más o con menos no hay forma honesta de saber
 * cuál es de cuál, así que se sigue con el del extractor y se alerta. Todos llevan {@code
 * FOTOS_COMPARTIDAS}, porque las fotos de la publicación pueden ser de cualquiera de ellos. Y no
 * pasan de {@link #TOPE_DE_PRODUCTOS}: un mensaje de catálogo con treinta líneas no son treinta
 * borradores, es algo que tiene que mirar una persona, y por eso los que quedan llevan además
 * {@code CONFIANZA_BAJA}.
 *
 * <h2>Las fotos (10 de octubre de 2026)</h2>
 *
 * <p>Con los productos ya leídos, el {@link LectorDeFotos} mira las fotos y {@link RepartoDeFotos}
 * decide cuáles son de quién. Un producto cuyas fotos se repartieron con respaldo —una referencia
 * impresa que coincide con la del texto, o el pie de un álbum— deja de llevar {@code
 * FOTOS_COMPARTIDAS}. Un álbum de diseños se parte en un producto por diseño, todos con el precio y
 * el texto del anuncio; esos no cuentan para {@link #TOPE_DE_PRODUCTOS}, que es de productos
 * escritos en el texto. El diseño que el lector separó sin nada impreso que lo confirme lleva
 * {@code CONFIANZA_BAJA}. Si la lectura falla, la publicación sigue como antes de existir: todas
 * las fotos para todos.
 */
public final class ExtraerProductoDePublicacion {

  /** Decidido por el negocio el 2 de octubre de 2026. */
  static final int TOPE_DE_PRODUCTOS = 5;

  private final ExtractorDeProductos extractor;
  private final LectorDeFotos lector;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final BigDecimal umbralDeConfianza;

  /** Sin lectura de fotos: el reparto de antes del 10 de octubre de 2026. */
  public ExtraerProductoDePublicacion(
      ExtractorDeProductos extractor, BigDecimal umbralDeConfianza) {
    this(extractor, fotos -> Optional.empty(), null, umbralDeConfianza);
  }

  /**
   * @param almacen de donde se leen las fotos para el lector; puede ser nulo solo si el lector está
   *     apagado
   */
  public ExtraerProductoDePublicacion(
      ExtractorDeProductos extractor,
      LectorDeFotos lector,
      AlmacenDeArchivosDeProveedor almacen,
      BigDecimal umbralDeConfianza) {
    this.extractor = Objects.requireNonNull(extractor);
    this.lector = Objects.requireNonNull(lector);
    this.almacen = almacen;
    Objects.requireNonNull(umbralDeConfianza, "El umbral de confianza no puede ser nulo.");
    if (umbralDeConfianza.compareTo(BigDecimal.ZERO) < 0
        || umbralDeConfianza.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("El umbral de confianza va de 0 a 1.");
    }
    this.umbralDeConfianza = umbralDeConfianza;
  }

  /**
   * @param mensajes los mensajes del lote por id; la publicación solo guarda identificadores
   * @return una evaluación por producto, en el orden del mensaje; vacía si el extractor no
   *     reconoció ninguno
   */
  public List<ExtraccionEvaluada> ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      LineaCatalogo lineaDelProveedor) {
    MensajeProveedor principal = mensajeDe(mensajes, publicacion.mensajePrincipalId());
    List<String> adicionales = new ArrayList<>();
    for (UUID id : publicacion.textosAdicionales()) {
      mensajeDe(mensajes, id).textoLegible().ifPresent(adicionales::add);
    }
    TextoDePublicacion texto =
        new TextoDePublicacion(principal.textoLegible().orElse(""), adicionales, lineaDelProveedor);

    ResultadoExtraccion resultado = extractor.extraer(texto);
    List<ProductoExtraido> productos = resultado.productos();
    boolean recortado = productos.size() > TOPE_DE_PRODUCTOS;
    if (recortado) {
      productos = productos.subList(0, TOPE_DE_PRODUCTOS);
    }
    boolean varios = productos.size() > 1;

    boolean sinFotos =
        publicacion.medios().stream()
            .map(mensajes::get)
            .noneMatch(m -> m != null && m.referenciaArchivo().isPresent());
    List<Dinero> preciosDelTexto = PatronDePrecio.extraerTodos(texto.completo());

    List<ProductoExtraido> contrastados =
        productos.stream().map(p -> p.contrastadoCon(texto.completo())).toList();
    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(
            contrastados,
            publicacion.medios(),
            sinFotos ? null : leerFotos(publicacion, mensajes, texto, contrastados));

    List<ExtraccionEvaluada> evaluadas = new ArrayList<>(repartidos.size());
    for (int i = 0; i < repartidos.size(); i++) {
      ProductoRepartido repartido = repartidos.get(i);
      ProductoExtraido producto = repartido.producto();
      Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
      // Los diseños de un álbum comparten el único precio del texto.
      boolean album = repartido.disenoDeAlbum();
      int posicionEnElTexto = album ? 0 : i;
      Optional<Dinero> delTexto =
          precioDelTexto(preciosDelTexto, posicionEnElTexto, contrastados.size());
      Dinero precio = contrastarPrecio(delTexto, producto.precioProveedorOpcional(), alertas);

      if (producto.tituloOpcional().isEmpty()) {
        alertas.add(AlertaBorrador.TITULO_VACIO);
      }
      if (producto.tipo() == TipoProductoProveedor.OTRO) {
        alertas.add(AlertaBorrador.TIPO_DESCONOCIDO);
      }
      if (recortado
          || producto.confianza().compareTo(umbralDeConfianza) < 0
          || repartido.respaldo() == Respaldo.DISENO_SIN_PIE) {
        alertas.add(AlertaBorrador.CONFIANZA_BAJA);
      }
      if (producto.esReplica()) {
        alertas.add(AlertaBorrador.REPLICA);
      }
      if (sinFotos) {
        alertas.add(AlertaBorrador.SIN_FOTOS);
      } else if (varios && repartido.exclusivaOpcional().isEmpty()) {
        alertas.add(AlertaBorrador.FOTOS_COMPARTIDAS);
      }
      evaluadas.add(
          new ExtraccionEvaluada(
              producto,
              resultado.jsonCrudo(),
              precio,
              alertas,
              resultado.uso(),
              repartido.fotos(),
              repartido.reparto(),
              varios || album ? repartido.exclusiva() : null,
              album));
    }
    return evaluadas;
  }

  /**
   * Las fotos legibles de la publicación, al lector. Lee del bucket, así que va fuera de cualquier
   * transacción, como la extracción. <b>Nada de aquí tumba la publicación ni el lote</b>: ni el
   * lector que falla ni el bucket que no responde a mitad de la lectura. Sin lectura, el reparto es
   * el de antes. Por eso se atrapa cualquier {@code RuntimeException} y no solo la del lector: una
   * que se escapara de aquí cerraría el lote entero en {@link ProcesarLoteDeIngesta}.
   */
  private LecturaDeFotos leerFotos(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      TextoDePublicacion texto,
      List<ProductoExtraido> productos) {
    if (almacen == null || productos.isEmpty()) {
      return null;
    }
    try {
      return leerFotosSinProteger(publicacion, mensajes, texto, productos);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private LecturaDeFotos leerFotosSinProteger(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      TextoDePublicacion texto,
      List<ProductoExtraido> productos) {
    List<FotosParaLeer.FotoParaLeer> fotos = new ArrayList<>();
    List<UUID> medios = publicacion.medios();
    for (int i = 0; i < medios.size(); i++) {
      int posicion = i;
      Optional.ofNullable(mensajes.get(medios.get(i)))
          .flatMap(MensajeProveedor::referenciaArchivo)
          .flatMap(almacen::leer)
          .ifPresent(bytes -> fotos.add(new FotosParaLeer.FotoParaLeer(posicion, bytes)));
    }
    if (fotos.isEmpty()) {
      return null;
    }
    List<FotosParaLeer.ProductoNombrado> nombrados =
        productos.stream()
            .map(
                p ->
                    new FotosParaLeer.ProductoNombrado(
                        p.titulo(), p.codigoReferenciaOpcional().orElse(null)))
            .toList();
    return lector.leer(new FotosParaLeer(texto.completo(), nombrados, fotos)).orElse(null);
  }

  /**
   * Con un solo producto, el primer precio del texto, como siempre: el «por difusión» va delante
   * del «después de 6». Con varios, el de su posición, y solo si hay uno por producto.
   */
  private static Optional<Dinero> precioDelTexto(List<Dinero> precios, int posicion, int cuantos) {
    if (precios.isEmpty()) {
      return Optional.empty();
    }
    if (cuantos == 1) {
      return Optional.of(precios.getFirst());
    }
    return precios.size() == cuantos ? Optional.of(precios.get(posicion)) : Optional.empty();
  }

  private static Dinero contrastarPrecio(
      Optional<Dinero> delTexto, Optional<Dinero> delExtractor, Set<AlertaBorrador> alertas) {
    if (delTexto.isPresent()) {
      if (delExtractor.isEmpty() || !delExtractor.get().equals(delTexto.get())) {
        alertas.add(AlertaBorrador.PRECIO_INCONSISTENTE);
      }
      return delTexto.get();
    }
    if (delExtractor.isPresent()) {
      alertas.add(AlertaBorrador.PRECIO_INCONSISTENTE);
      return delExtractor.get();
    }
    alertas.add(AlertaBorrador.SIN_PRECIO);
    return null;
  }

  private static MensajeProveedor mensajeDe(Map<UUID, MensajeProveedor> mensajes, UUID id) {
    MensajeProveedor mensaje = mensajes.get(id);
    if (mensaje == null) {
      throw new IllegalStateException(
          "La publicación apunta a un mensaje que no está en el lote: " + id);
    }
    return mensaje;
  }
}
