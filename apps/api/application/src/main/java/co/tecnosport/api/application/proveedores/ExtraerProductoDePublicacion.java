package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.CalzadoSinTenis;
import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.LecturaDelTenis;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PatronDePrecio;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
 * <p>La réplica sale solo del texto: una marca que el lector lee en la foto no la alerta. Un logo
 * en una suela o en una lengüeta no dice que el proveedor la venda como réplica, y la alerta
 * terminaba en productos que el texto no relacionaba con ninguna marca (decidido por el negocio el
 * 11 de octubre de 2026). Lo que lee sigue sirviendo para repartir las fotos entre las marcas que
 * el texto sí nombra ({@link RepartoDeFotos}).
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
 *
 * <h2>Una foto, un producto (25 de octubre de 2026)</h2>
 *
 * <p>Al proveedor que lo tiene marcado ({@code AjustesDeIngesta#unaFotoUnProducto}), la tanda se
 * parte en un producto por foto con {@link RepartoDeFotos#unaPorFoto}, todos con el precio del
 * texto. El texto de Tenis Al Pie es solo el precio —«Detal :$180.000 / X mayor 💰135»—, y el
 * extractor puede no ver en él ningún producto: entonces la tanda se parte igual, desde un producto
 * que solo tiene ese precio (ADR-0089). En calzado, el producto es el tenis y no la foto.
 *
 * <h2>El calzado (26 de octubre de 2026)</h2>
 *
 * <p>Las fotos de todo proveedor de calzado las lee el lector de calzado, un modelo más capaz que
 * reconoce la marca y el modelo de un tenis por la forma, y el título de un tenis de cualquier
 * proveedor se completa con lo que se ve en sus fotos si el texto no dice el modelo ({@link
 * ProductoExtraido#conElTenisDeSusFotos}). El título que lleva un modelo dudoso alerta {@code
 * MODELO_POR_CONFIRMAR} (ADR-0090).
 */
public final class ExtraerProductoDePublicacion {

  /** Decidido por el negocio el 2 de octubre de 2026. */
  static final int TOPE_DE_PRODUCTOS = 5;

  /** El uso de un anuncio que se descartó sin llamar al extractor. */
  private static final UsoDelExtractor SIN_LEER = new UsoDelExtractor("ninguno", 0, 0, 0);

  private final ExtractorDeProductos extractor;
  private final LectorDeFotos lector;
  private final LectorDeFotos lectorDeCalzado;
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
    this(extractor, lector, lector, almacen, umbralDeConfianza);
  }

  /**
   * @param lectorDeCalzado el que lee las fotos de un proveedor de calzado (ADR-0090): tiene que
   *     reconocer la marca y el modelo de un tenis por la forma
   * @param almacen de donde se leen las fotos para el lector; puede ser nulo solo si el lector está
   *     apagado
   */
  public ExtraerProductoDePublicacion(
      ExtractorDeProductos extractor,
      LectorDeFotos lector,
      LectorDeFotos lectorDeCalzado,
      AlmacenDeArchivosDeProveedor almacen,
      BigDecimal umbralDeConfianza) {
    this.extractor = Objects.requireNonNull(extractor);
    this.lector = Objects.requireNonNull(lector);
    this.lectorDeCalzado = Objects.requireNonNull(lectorDeCalzado);
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
    return ejecutar(publicacion, mensajes, lineaDelProveedor, false);
  }

  /**
   * @param unaFotoUnProducto si el proveedor manda cada producto en su propia foto (ADR-0089)
   */
  public List<ExtraccionEvaluada> ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      LineaCatalogo lineaDelProveedor,
      boolean unaFotoUnProducto) {
    MensajeProveedor principal = mensajeDe(mensajes, publicacion.mensajePrincipalId());
    List<String> adicionales = new ArrayList<>();
    for (UUID id : publicacion.textosAdicionales()) {
      mensajeDe(mensajes, id).textoLegible().ifPresent(adicionales::add);
    }
    TextoDePublicacion texto =
        new TextoDePublicacion(principal.textoLegible().orElse(""), adicionales, lineaDelProveedor);

    // La chola o la sandalia que el texto ya nombra no se lee: ni el extractor ni el lector de
    // fotos
    // tienen nada que decidir en ella (ADR-0092, decisión 7).
    if (lineaDelProveedor == LineaCatalogo.CALZADO) {
      Optional<CierreDePublicacion> porElTexto = CalzadoSinTenis.porElTexto(texto.completo());
      if (porElTexto.isPresent()) {
        ProductoExtraido base =
            soloConElPrecio(PatronDePrecio.extraerTodos(texto.completo()), lineaDelProveedor);
        return List.of(
            new ExtraccionEvaluada(
                    base,
                    "{}",
                    base.precioProveedorOpcional().orElse(null),
                    Set.of(),
                    SIN_LEER,
                    List.of(),
                    null,
                    null,
                    false,
                    List.of(),
                    null)
                .descartada(porElTexto.get()));
      }
    }

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
    List<ProductoRepartido> repartidos;
    LecturaDeFotos lectura;
    int productosDelTexto = contrastados.size();
    ProductoExtraido base = null;
    if (unaFotoUnProducto && !sinFotos) {
      base =
          contrastados.isEmpty()
              ? soloConElPrecio(preciosDelTexto, lineaDelProveedor)
              : contrastados.getFirst();
      boolean calzado = lineaDelProveedor == LineaCatalogo.CALZADO;
      lectura =
          leerFotos(
              calzado ? lectorDeCalzado : lector, publicacion, mensajes, texto, List.of(base));
      repartidos = RepartoDeFotos.unaPorFoto(base, publicacion.medios(), lectura, calzado);
      varios = false;
      productosDelTexto = 1;
    } else {
      LectorDeFotos elLector =
          lineaDelProveedor == LineaCatalogo.CALZADO ? lectorDeCalzado : lector;
      lectura = sinFotos ? null : leerFotos(elLector, publicacion, mensajes, texto, contrastados);
      repartidos = RepartoDeFotos.repartir(contrastados, publicacion.medios(), lectura);
    }

    // La tienda solo vende tenis: el anuncio de calzado en el que ninguna foto muestra uno no deja
    // borrador, y tampoco el que no tiene fotos que leer (ADR-0092). Un agotado sí sigue: agota lo
    // que ya se vende.
    Optional<CierreDePublicacion> sinTenis =
        lineaDelProveedor == LineaCatalogo.CALZADO ? CalzadoSinTenis.en(lectura) : Optional.empty();
    List<ExtraccionEvaluada> evaluadas = new ArrayList<>(repartidos.size());
    if (repartidos.isEmpty() && base != null && sinTenis.isPresent()) {
      // La tanda de Tenis Al Pie sin un tenis: el reparto ya dejó fuera sus fotos, y queda dicho
      // por qué en vez de «sin producto».
      evaluadas.add(
          new ExtraccionEvaluada(
                  base, resultado.jsonCrudo(), base.precioProveedor(), Set.of(), resultado.uso())
              .descartada(sinTenis.get()));
    }
    for (int i = 0; i < repartidos.size(); i++) {
      ProductoRepartido repartido = repartidos.get(i);
      // Solo si sus fotos son suyas: con varios productos en el texto o fotos de todos, los colores
      // de la foto pueden ser del otro, y el borrador nacería con variantes que no tiene.
      ProductoExtraido producto =
          varios || repartido.compartidas()
              ? repartido.producto()
              : repartido
                  .producto()
                  .conTonosDeLasFotos(
                      repartido.fotos().stream()
                          .map(repartido.reparto().tonosSugeridos()::get)
                          .filter(Objects::nonNull)
                          .toList())
                  .conTallasDeLasFotos(tallasImpresas(publicacion, repartido.fotos(), lectura));
      // El tenis de otro proveedor, titulado también con lo que se ve en sus fotos (ADR-0090). Solo
      // si las fotos son suyas: con varios productos, la marca de la foto puede ser del otro.
      Optional<LecturaDeCalzado> delTenis = repartido.calzadoOpcional();
      if (delTenis.isEmpty() && !varios && !repartido.compartidas()) {
        List<LecturaDeFoto> deSusFotos = lecturasDe(publicacion, repartido.fotos(), lectura);
        delTenis = LecturaDelTenis.deLasQueMuestranUnTenis(deSusFotos);
        if (delTenis.isPresent()) {
          producto =
              producto.conElTenisDeSusFotos(
                  delTenis.get(), LecturaDelTenis.marcasLeidas(deSusFotos));
        }
      }
      Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
      // Los diseños de un álbum comparten el único precio del texto.
      boolean album = repartido.disenoDeAlbum();
      int posicionEnElTexto = album ? 0 : i;
      Optional<Dinero> delTexto =
          precioDelTexto(preciosDelTexto, posicionEnElTexto, productosDelTexto);
      Dinero precio = contrastarPrecio(delTexto, producto.precioProveedorOpcional(), alertas);

      if (producto.tituloOpcional().isEmpty()) {
        alertas.add(AlertaBorrador.TITULO_VACIO);
      }
      if (producto.tipo() == TipoProductoProveedor.OTRO) {
        alertas.add(AlertaBorrador.TIPO_DESCONOCIDO);
      }
      if (recortado || producto.confianza().compareTo(umbralDeConfianza) < 0) {
        alertas.add(AlertaBorrador.CONFIANZA_BAJA);
      }
      if (producto.esReplica()) {
        alertas.add(AlertaBorrador.REPLICA);
      }
      if (llevaUnModeloPorConfirmar(producto, delTenis)) {
        alertas.add(AlertaBorrador.MODELO_POR_CONFIRMAR);
      }
      if (sinFotos) {
        alertas.add(AlertaBorrador.SIN_FOTOS);
      } else if ((varios && repartido.exclusivaOpcional().isEmpty()) || repartido.compartidas()) {
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
              album,
              varios || album ? repartido.conSuCodigo() : repartido.fotos(),
              // El de otro proveedor también: de ahí sale su género si el texto no lo dice.
              delTenis.orElse(null),
              producto.estaAgotado() ? null : sinTenis.orElse(null)));
    }
    return evaluadas;
  }

  /**
   * El producto de una tanda cuyo texto es solo el precio: sin título, sin tipo y con el precio que
   * el texto escribe. El título, el tipo y la marca los pone después la foto. La confianza es la
   * del umbral: no hay nada del modelo en que confiar ni que desconfiar.
   */
  private ProductoExtraido soloConElPrecio(List<Dinero> preciosDelTexto, LineaCatalogo linea) {
    return new ProductoExtraido(
        true,
        false,
        null,
        linea,
        TipoProductoProveedor.OTRO,
        preciosDelTexto.isEmpty() ? null : preciosDelTexto.getFirst(),
        null,
        null,
        List.of(),
        null,
        null,
        null,
        false,
        umbralDeConfianza,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  /**
   * Las fotos legibles de la publicación, al lector. Lee del bucket, así que va fuera de cualquier
   * transacción, como la extracción. <b>Nada de aquí tumba la publicación ni el lote</b>: ni el
   * lector que falla ni el bucket que no responde a mitad de la lectura. Sin lectura, el reparto es
   * el de antes. Por eso se atrapa cualquier {@code RuntimeException} y no solo la del lector: una
   * que se escapara de aquí cerraría el lote entero en {@link ProcesarLoteDeIngesta}. Salvo {@link
   * ExtractorNoDisponibleException}, que no cierra el lote sino que lo pausa.
   */
  private LecturaDeFotos leerFotos(
      LectorDeFotos elLector,
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      TextoDePublicacion texto,
      List<ProductoExtraido> productos) {
    if (almacen == null || productos.isEmpty()) {
      return null;
    }
    try {
      return leerFotosSinProteger(elLector, publicacion, mensajes, texto, productos);
    } catch (ExtractorNoDisponibleException e) {
      // La cuenta, no la foto: la publicación siguiente fallaría igual, y sin lectura saldrían
      // borradores peor repartidos. Que el lote se pause, como con el extractor.
      throw e;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private LecturaDeFotos leerFotosSinProteger(
      LectorDeFotos elLector,
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
                        p.titulo(), p.codigoReferenciaOpcional().orElse(null), p.marcas()))
            .toList();
    return elLector.leer(new FotosParaLeer(texto.completo(), nombrados, fotos)).orElse(null);
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

  /** El título lleva el modelo que el lector cree ver sin estar seguro (ADR-0090). */
  private static boolean llevaUnModeloPorConfirmar(
      ProductoExtraido producto, Optional<LecturaDeCalzado> delTenis) {
    return delTenis.filter(LecturaDeCalzado::modeloPorConfirmar).isPresent()
        && producto
            .tituloOpcional()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .map(t -> t.contains(delTenis.get().modelo().toLowerCase(Locale.ROOT)))
            .orElse(false);
  }

  /** Lo que el lector vio en las fotos de este producto, en el orden de la publicación. */
  private static List<LecturaDeFoto> lecturasDe(
      PublicacionProveedor publicacion, List<UUID> suyas, LecturaDeFotos lectura) {
    if (lectura == null) {
      return List.of();
    }
    List<LecturaDeFoto> leidas = new ArrayList<>();
    List<UUID> medios = publicacion.medios();
    for (int i = 0; i < medios.size(); i++) {
      if (suyas.contains(medios.get(i))) {
        lectura.deLaFoto(i).ifPresent(leidas::add);
      }
    }
    return leidas;
  }

  /**
   * Las tallas del rótulo impreso en las fotos de este producto, en el orden de la publicación
   * ({@link LecturaDeFotos.LecturaDeFoto#tallasImpresas}).
   */
  private static List<String> tallasImpresas(
      PublicacionProveedor publicacion, List<UUID> suyas, LecturaDeFotos lectura) {
    if (lectura == null) {
      return List.of();
    }
    List<String> tallas = new ArrayList<>();
    List<UUID> medios = publicacion.medios();
    for (int i = 0; i < medios.size(); i++) {
      if (suyas.contains(medios.get(i))) {
        lectura.deLaFoto(i).ifPresent(l -> tallas.addAll(l.tallasImpresas()));
      }
    }
    return tallas;
  }
}
