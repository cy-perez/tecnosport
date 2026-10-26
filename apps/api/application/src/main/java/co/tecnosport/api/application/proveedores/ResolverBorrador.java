package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor.PrendaDeProveedor;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AjusteDelAnuncio;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.ArmadoDeSet;
import co.tecnosport.api.domain.proveedores.ArmadorDeSets;
import co.tecnosport.api.domain.proveedores.ArmadorDeSets.SetArmado;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.CalculadoraDeMargen;
import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.GeneroDeCalzado;
import co.tecnosport.api.domain.proveedores.GeneroDelAnuncio;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.MotivoDePublicacion;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.PrendaAcompanante;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TallasPorGenero;
import co.tecnosport.api.domain.proveedores.TextoDeAnuncio;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import co.tecnosport.api.domain.proveedores.TituloPorGenero;
import co.tecnosport.api.domain.proveedores.TopeDeCostoPorTipo;
import co.tecnosport.api.domain.proveedores.TopesDeGanancia;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Con la extracción ya evaluada, decide qué es la publicación: un producto nuevo que espera
 * revisión, una renovación de uno que ya existe, un aviso de agotado, o nada.
 *
 * <h2>Cómo se reconoce un producto que ya existe</h2>
 *
 * <p>Primero por la huella, que es exacta: la del código de referencia cuando el proveedor marca la
 * prenda —«(Q339)»—, y si no la del anuncio, que lleva la fecha del mensaje ({@link
 * HuellaProveedor}). Si no cae, por la foto: el pHash de la primera foto contra los de los
 * productos del mismo proveedor, dentro del umbral de Hamming. <b>La foto reconoce aunque el texto
 * o el precio cambien</b>, y ahí es donde nace {@code PRECIO_CAMBIO}.
 *
 * <h2>El mismo texto no es el mismo producto (9 de octubre de 2026)</h2>
 *
 * <p>La Riverah repite el texto de un anuncio con otra prenda: el «Busito manga larga» a 58.000
 * salió azul y, horas después, gris. Por eso un anuncio sin código solo se descarta por repetido
 * cuando hay un borrador en revisión con <b>el mismo texto y la misma foto</b>. El texto es el que
 * escribió el proveedor, normalizado ({@link TextoDeAnuncio}), no el título del extractor, que no
 * sale igual dos veces; y la foto es cualquiera de las de un anuncio a la distancia de Hamming del
 * umbral o menos de cualquiera de las del otro, no solo la principal (los dos, desde el 9 de
 * octubre de 2026). Con el texto igual y otra foto es otra prenda, y abre su propio borrador. Si
 * uno de los dos no tiene foto con la que comparar, no hay cómo saber que es el mismo, y tampoco se
 * descarta: un borrador de más se elimina en el panel; una prenda descartada no vuelve. Con código
 * de referencia el código basta.
 *
 * <h2>El chat de caballero (9 de octubre de 2026)</h2>
 *
 * <p>Meraki publica desde el mismo número en dos chats, el general y el de caballero, y el general
 * repite anuncios del otro. Lo que llega en el general con el mismo texto que un anuncio del de
 * caballero se descarta, sin mirar fotos: es el mismo anuncio y su lugar es el de caballero. Si el
 * general se procesó primero y ese anuncio ya espera revisión, cuando llega el de caballero aquel
 * se rechaza con el motivo escrito, y queda el de caballero. Se suben en ese orden —primero el de
 * caballero— para no tener que rechazar nada, pero el resultado no depende del orden.
 *
 * <h2>Las fotos repartidas (10 de octubre de 2026)</h2>
 *
 * <p>Cuando la extracción repartió las fotos ({@link ExtraccionEvaluada#exclusiva()}), cada
 * producto usa las suyas para reconocerse y su foto exclusiva como huella visual, aunque el mensaje
 * traiga varios. El diseño de un álbum sin código comparte el texto, el precio y la fecha con los
 * demás diseños: su huella lleva además la de su foto ({@link HuellaProveedor#deDisenoDeAnuncio}),
 * o los trece diseños serían el mismo producto al aprobar el segundo.
 *
 * <h2>Lo que no se le compra al proveedor (12 de octubre de 2026)</h2>
 *
 * <p>Si un producto del anuncio es de un tipo que el proveedor tiene excluido ({@link
 * Proveedor#tiposExcluidos()}), se descartan todos, con ese motivo y antes de cualquier otra cosa:
 * a Violeta no se le compran pantalones, y el anuncio de una blusa con su jean no deja ni la blusa.
 * Tampoco renueva: si la blusa ya estuviera en el catálogo, ese anuncio no le refresca la
 * disponibilidad. <b>Lo que sí pasa es un agotado</b>: el jean que se publicó antes de excluir el
 * tipo, o la blusa del anuncio, se agota cuando el proveedor lo anuncia agotado, porque dejarlo a
 * la venta es venderle a alguien lo que no hay.
 *
 * <h2>Lo que cuesta de más (13 de octubre de 2026)</h2>
 *
 * <p>El pantalón, el jogger o el short que el proveedor anuncia por encima de lo que se le paga por
 * su tipo ({@link TopeDeCostoPorTipo}) se descarta, solo ese producto y no los demás del anuncio, y
 * sin renovar: si ya estuviera en el catálogo, a ese precio ya no se le compra. Un agotado sí pasa.
 *
 * <h2>Los sets (12 de octubre de 2026)</h2>
 *
 * <p>A un proveedor que arma sets ({@link Proveedor#armaSets()}, hoy La Riverah) la prenda que
 * anuncia con su blusa o su gorra le queda como set antes de todo lo demás ({@link ArmadorDeSets}):
 * con el título, el tipo y el costo del set, y el precio de venta sugerido sobre ese costo. Su
 * huella es la de la prenda más el acompañante ({@link HuellaProveedor#deSet}), y por la foto solo
 * se reconoce entre los sets con el mismo acompañante: la foto es la de la prenda, y la prenda que
 * ya se vendiera sola se renovaría con el costo del set, o al revés. Un agotado de la prenda agota
 * también sus sets ({@code ADR-0083}).
 *
 * <h2>Las prendas de un álbum (13 de octubre de 2026)</h2>
 *
 * <p>El álbum de La Riverah es un solo producto, y el SKU de cada diseño quedó al aprobar en las
 * variantes de su prenda. Un anuncio con ese SKU —«AGOTADO polo RV102376»— es de esa prenda: si
 * viene agotado, sus variantes salen de la venta y las demás siguen; si se agotan todas, se agota
 * el producto. Si viene sin agotar, la prenda vuelve a la venta y el producto se renueva.
 *
 * <p>Una renovación no crea un borrador para revisar: actualiza la última vista, reactiva el
 * producto si estaba oculto y deja una constancia {@code RENOVACION_APLICADA}. Un agotado sobre un
 * producto existente lo agota de inmediato. Un agotado sobre algo que no está en el catálogo no es
 * nada que publicar, y se descarta con ese motivo.
 */
public final class ResolverBorrador {

  private static final CierreDePublicacion SIN_PRODUCTO =
      CierreDePublicacion.de(MotivoDePublicacion.SIN_PRODUCTO);

  static final String VIENE_EN_EL_CHAT_DE_CABALLERO =
      "El anuncio viene en el chat de caballero del proveedor; se procesa desde ese archivo.";

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final CalculadorDePHash calculadorDePHash;
  private final Reloj reloj;
  private final Map<LineaCatalogo, BigDecimal> factorPorLinea;
  private final TopesDeGanancia topesDeGanancia;
  private final int umbralHamming;
  private final ExistenciaBajoConfirmacion existenciaBajoConfirmacion;

  public ResolverBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos repositorioProductos,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      AlmacenDeArchivosDeProveedor almacen,
      CalculadorDePHash calculadorDePHash,
      Reloj reloj,
      Map<LineaCatalogo, BigDecimal> factorPorLinea,
      TopesDeGanancia topesDeGanancia,
      int umbralHamming) {
    this(
        repositorioBorradores,
        productosDeProveedor,
        repositorioProductos,
        repositorioPublicaciones,
        almacen,
        calculadorDePHash,
        reloj,
        factorPorLinea,
        topesDeGanancia,
        umbralHamming,
        null);
  }

  /**
   * @param existenciaBajoConfirmacion repone lo que se vende bajo confirmación cuando vuelve a
   *     aparecer (ADR-0089); nula solo donde ningún proveedor vende así
   */
  public ResolverBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioProductos repositorioProductos,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      AlmacenDeArchivosDeProveedor almacen,
      CalculadorDePHash calculadorDePHash,
      Reloj reloj,
      Map<LineaCatalogo, BigDecimal> factorPorLinea,
      TopesDeGanancia topesDeGanancia,
      int umbralHamming,
      ExistenciaBajoConfirmacion existenciaBajoConfirmacion) {
    this.existenciaBajoConfirmacion = existenciaBajoConfirmacion;
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.almacen = Objects.requireNonNull(almacen);
    this.calculadorDePHash = Objects.requireNonNull(calculadorDePHash);
    this.reloj = Objects.requireNonNull(reloj);
    this.factorPorLinea = Map.copyOf(Objects.requireNonNull(factorPorLinea));
    this.topesDeGanancia = Objects.requireNonNull(topesDeGanancia);
    if (umbralHamming < 0) {
      throw new IllegalArgumentException("El umbral de Hamming no puede ser negativo.");
    }
    this.umbralHamming = umbralHamming;
  }

  /**
   * La forma cómoda: calcula los pHash y carga las huellas visuales aquí mismo. Lee del bucket y
   * consulta al repositorio, así que <b>no se llama dentro de una transacción</b>; el lote usa la
   * otra sobrecarga con las dos cosas ya preparadas fuera.
   */
  public List<Resolucion> ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      List<ExtraccionEvaluada> evaluadas) {
    return ejecutar(
        publicacion,
        mensajes,
        proveedor,
        evaluadas,
        necesitaPHashes(evaluadas) ? pHashesDe(publicacion, mensajes) : Map.of(),
        huellasVisualesDe(proveedor.id()));
  }

  /**
   * Resuelve cada producto de la publicación y después decide la publicación una sola vez: queda
   * extraída si al menos uno terminó en borrador o en renovación, y descartada solo si se
   * descartaron todos.
   *
   * <p><b>Con varios productos no se usa el pHash</b>, ni para reconocer ni para guardarlo: la
   * primera foto puede ser de cualquiera de ellos, y con ella el jean del conjunto se reconocería
   * después como la chaqueta. Esos borradores nacen sin huella visual y la reciben al aprobarse, de
   * la foto que la persona marque como principal.
   *
   * @param pHashes los de las fotos legibles de la publicación, por mensaje, ya calculados fuera de
   *     la transacción. Vacío si no hay ninguna o no hacen falta ({@link #necesitaPHashes})
   * @param huellasVisuales las de los productos que ya existen del proveedor, cargadas una vez por
   *     lote
   * @return una resolución por producto, en el orden del mensaje; una sola, descartada, si el
   *     extractor no reconoció ninguno
   */
  public List<Resolucion> ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      List<ExtraccionEvaluada> evaluadas,
      Map<UUID, PHash> pHashes,
      List<HuellaVisual> huellasVisuales) {
    return ejecutar(
        publicacion, mensajes, proveedor, evaluadas, pHashes, huellasVisuales, ChatDelLote.unico());
  }

  /**
   * Como la de arriba, sabiendo de qué chat viene el lote: la de un proveedor que publica en un
   * chat general y uno de caballero.
   */
  public List<Resolucion> ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      List<ExtraccionEvaluada> evaluadas,
      Map<UUID, PHash> pHashes,
      List<HuellaVisual> huellasVisuales,
      ChatDelLote chat) {
    if (evaluadas.isEmpty()) {
      return List.of(descartar(publicacion, SIN_PRODUCTO));
    }
    String textoDelAnuncio =
        Optional.ofNullable(mensajes.get(publicacion.mensajePrincipalId()))
            .flatMap(MensajeProveedor::textoLegible)
            .orElse(null);
    Optional<CierreDePublicacion> excluido =
        evaluadas.stream()
            .map(e -> e.producto().tipo())
            .filter(proveedor::excluye)
            .findFirst()
            .map(tipo -> new CierreDePublicacion(MotivoDePublicacion.TIPO_EXCLUIDO, tipo.name()));
    List<Resolucion> resoluciones = new ArrayList<>(evaluadas.size());
    boolean vieneEnElDeCaballero =
        !chat.deCaballero()
            && chat.textosDeCaballero().stream()
                .anyMatch(t -> TextoDeAnuncio.mismoAnuncio(t, textoDelAnuncio));
    for (ExtraccionEvaluada evaluada : evaluadas) {
      // El calzado que no es un tenis, o que nadie vio, se descartó al leer sus fotos (ADR-0092).
      if (evaluada.descarteOpcional().isPresent()) {
        resoluciones.add(Resolucion.descartada(evaluada.descarteOpcional().get()));
        continue;
      }
      resoluciones.add(
          excluido.isPresent() && !evaluada.producto().estaAgotado()
              ? Resolucion.descartada(excluido.get())
              : pasaDelTope(evaluada, textoDelAnuncio)
                  ? Resolucion.descartada(
                      new CierreDePublicacion(
                          MotivoDePublicacion.PRECIO_SOBRE_TOPE,
                          precioDeLaPrenda(
                                  evaluada.producto(), evaluada.precioProveedor(), textoDelAnuncio)
                              .valor()
                              .toBigInteger()
                              .toString()))
                  : vieneEnElDeCaballero
                      ? Resolucion.descartada(
                          CierreDePublicacion.de(MotivoDePublicacion.VIENE_EN_CHAT_DE_CABALLERO))
                      : resolverUno(
                          publicacion,
                          mensajes,
                          proveedor,
                          evaluada,
                          textoDelAnuncio,
                          fotosDe(publicacion, evaluada, evaluadas.size(), pHashes),
                          huellasVisuales));
    }
    if (chat.deCaballero()) {
      rechazarLosDelChatGeneral(proveedor, textoDelAnuncio);
    }
    boolean algunoSeQuedo =
        resoluciones.stream().anyMatch(r -> r.tipo() != TipoDeResolucion.DESCARTADA);
    if (algunoSeQuedo) {
      publicacion.marcarExtraida();
    } else {
      // Con varios productos descartados por razones distintas, la del primero: el panel muestra
      // una por publicación, y el orden es el del mensaje.
      publicacion.descartar(
          resoluciones.stream()
              .map(Resolucion::cierre)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(SIN_PRODUCTO));
    }
    repositorioPublicaciones.actualizar(publicacion);
    return resoluciones;
  }

  private Resolucion resolverUno(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      ExtraccionEvaluada evaluada,
      String textoDelAnuncio,
      List<PHash> fotos,
      List<HuellaVisual> huellasVisuales) {
    ProductoExtraido extraido = evaluada.producto();
    if (!extraido.esProducto()) {
      return Resolucion.descartada(SIN_PRODUCTO);
    }

    Dinero precio = evaluada.precioProveedor();
    Set<AlertaBorrador> alertasDelSet = EnumSet.noneOf(AlertaBorrador.class);
    Optional<SetArmado> set =
        proveedor.armaSets()
            ? ArmadorDeSets.armar(extraido, precioDeLaPrenda(extraido, precio, textoDelAnuncio))
            : Optional.empty();
    if (set.isPresent()) {
      extraido = set.get().producto();
      precio = set.get().costo();
      // El costo del set sale de lo que el modelo leyó del combo o del acompañante: si no está
      // escrito en el anuncio, nadie lo contrastó, y quien revisa tiene que saberlo.
      if (!set.get().preciosEscritosEn(textoDelAnuncio)) {
        alertasDelSet.add(AlertaBorrador.PRECIO_INCONSISTENTE);
      }
      if (!set.get().conDescripcionDelSet()) {
        alertasDelSet.add(AlertaBorrador.SET_SIN_DESCRIPCION);
      }
    } else if (proveedor.armaSets()
        && !extraido.estaAgotado()
        && ArmadorDeSets.anunciaAcompanante(textoDelAnuncio, extraido.tipo())) {
      alertasDelSet.add(AlertaBorrador.ACOMPANANTE_SIN_LEER);
    }
    PrendaAcompanante acompanante = set.map(s -> s.armado().acompanante()).orElse(null);
    // Las tallas que el proveedor de calzado maneja siempre, por el género que se vio en la foto:
    // el tenis de dama de Tenis Al Pie va de la 36 a la 39 EUR, el de caballero de la 40 a la 44, y
    // el unisex con las dos (ADR-0089). Sin género leído, unisex: quien revisa lo corrige.
    Optional<TallasPorGenero> tallasDelProveedor =
        extraido.tipo() == TipoProductoProveedor.TENIS
            ? proveedor.ajustesDeIngesta().tallasPorGeneroOpcional()
            : Optional.empty();
    // El texto de un anuncio por tenis —Imperio Wicho: «IMPORTADO CABALLERO»— dice para quién es, y
    // manda sobre la foto (ADR-0092). El de una tanda de Tenis Al Pie es de todos sus tenis: ahí
    // manda la foto de cada uno.
    Optional<GeneroDeCalzado> delTextoDelAnuncio =
        extraido.tipo() == TipoProductoProveedor.TENIS
                && !proveedor.ajustesDeIngesta().unaFotoUnProducto()
            ? GeneroDelAnuncio.de(textoDelAnuncio)
            : Optional.empty();
    GeneroDeCalzado genero =
        delTextoDelAnuncio
            .or(() -> evaluada.calzadoOpcional().flatMap(LecturaDeCalzado::generoOpcional))
            .orElse(
                tallasDelProveedor.isPresent() || evaluada.calzadoOpcional().isPresent()
                    ? GeneroDeCalzado.UNISEX
                    : null);
    if (tallasDelProveedor.isPresent()) {
      extraido = extraido.conTallas(Tallas.lista(tallasDelProveedor.get().delProveedor(genero)));
    }
    Optional<String> codigo = extraido.codigoReferenciaOpcional();
    HuellaProveedor delTexto =
        extraido.tituloOpcional().isPresent() && precio != null
            ? HuellaProveedor.calcular(proveedor.id(), extraido.titulo(), precio)
            : null;
    HuellaProveedor huella;
    if (codigo.isPresent()) {
      huella = HuellaProveedor.deReferencia(proveedor.id(), codigo.get());
    } else if (delTexto != null && evaluada.disenoDeAlbum()) {
      huella =
          HuellaProveedor.deDisenoDeAnuncio(
              proveedor.id(),
              extraido.titulo(),
              precio,
              publicacion.fecha(),
              fotos.isEmpty()
                  ? evaluada.exclusivaOpcional().map(UUID::toString).orElse("")
                  : fotos.getFirst().hex());
    } else if (delTexto != null) {
      huella =
          HuellaProveedor.deAnuncio(proveedor.id(), extraido.titulo(), precio, publicacion.fecha());
    } else {
      huella = null;
    }
    if (huella != null && set.isPresent()) {
      huella = HuellaProveedor.deSet(huella, set.get().armado().acompanante());
    }
    PHash pHash = fotos.isEmpty() ? null : fotos.getFirst();
    Instant ahora = reloj.ahora();

    // El SKU de una prenda de un álbum ya aprobado: el producto es el álbum, y lo que el proveedor
    // agota o vuelve a anunciar es esa prenda sola. Y el álbum que vuelve con los mismos SKU es el
    // mismo producto, empiece por la foto que empiece.
    List<String> referencias =
        codigo.map(List::of).orElseGet(() -> evaluada.reparto().referenciasDelAlbum());
    if (!referencias.isEmpty()) {
      Optional<Resolucion> deLasPrendas =
          resolverPrendas(
              publicacion,
              proveedor,
              evaluada,
              extraido,
              set.map(SetArmado::armado).orElse(null),
              referencias,
              huella,
              pHash,
              precio,
              ahora);
      if (deLasPrendas.isPresent()) {
        return deLasPrendas.get();
      }
    }

    // Por la foto, solo entre los de su misma clase: la foto de un set es la de su prenda, y si no
    // la prenda sola que ya estuviera en el catálogo se renovaría con el costo del set, o al revés.
    Optional<Producto> existente =
        buscarExistente(proveedor.id(), huella, pHash, huellasVisuales, acompanante);
    if (extraido.estaAgotado() && proveedor.armaSets() && set.isEmpty()) {
      // «AGOTADO polo RV102376», sin los precios de la gorra: no arma set, pero el set de esa misma
      // prenda tampoco se puede seguir vendiendo.
      List<Producto> sets = setsDeLaPrenda(proveedor.id(), huella, pHash, huellasVisuales);
      sets.stream()
          .filter(s -> existente.map(e -> !e.id().equals(s.id())).orElse(true))
          .forEach(
              s -> {
                s.marcarAgotadoPorProveedor(publicacion.fecha());
                repositorioProductos.actualizar(s);
              });
      if (existente.isEmpty() && !sets.isEmpty()) {
        return renovar(
            publicacion,
            proveedor,
            evaluada,
            extraido,
            null,
            sets.getFirst(),
            huella,
            pHash,
            precio,
            ahora);
      }
    }
    if (existente.isPresent()) {
      return renovar(
          publicacion,
          proveedor,
          evaluada,
          extraido,
          set.map(SetArmado::armado).orElse(null),
          existente.get(),
          huella,
          pHash,
          precio,
          ahora);
    }
    if (extraido.estaAgotado()) {
      return Resolucion.descartada(
          CierreDePublicacion.de(MotivoDePublicacion.AGOTADO_FUERA_DE_CATALOGO));
    }
    if (codigo.isPresent()) {
      Optional<UUID> enRevision =
          repositorioBorradores.buscarEnRevisionConHuella(proveedor.id(), huella);
      if (enRevision.isPresent()) {
        // La misma referencia antes de que alguien apruebe la primera. Un segundo borrador solo
        // sirve para chocar con el índice único al aprobarlo; sus fotos sí sirven, y van a aquel.
        int sumadas = sumarFotos(enRevision.get(), evaluada, mensajes);
        return Resolucion.descartada(
            new CierreDePublicacion(
                MotivoDePublicacion.MISMA_REFERENCIA_EN_REVISION,
                sumadas == 0 ? null : String.valueOf(sumadas)));
      }
    }
    if (codigo.isEmpty() && esAnuncioRepetido(proveedor, publicacion, textoDelAnuncio, fotos)) {
      return Resolucion.descartada(CierreDePublicacion.de(MotivoDePublicacion.ANUNCIO_REPETIDO));
    }

    Dinero precioSugerido =
        precio == null
            ? null
            : CalculadoraDeMargen.sugerir(precio, factorDe(proveedor, extraido), topesDeGanancia);
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido,
            evaluada.jsonCrudo(),
            precio,
            precioSugerido,
            huella,
            pHash,
            conLasDelSet(evaluada.alertas(), alertasDelSet),
            evaluada.reparto(),
            ahora);
    set.ifPresent(s -> BorradorProducto.conSet(borrador, s.armado()));
    // Un bolso, un morral o una tula del mismo anuncio no tallan: ni se corren ni se parten.
    boolean talla = extraido.linea() != LineaCatalogo.BOLSOS;
    BorradorProducto.conGeneroDeCalzado(borrador, genero);
    if (tallasDelProveedor.isPresent()) {
      // Las del proveedor ya son las de su género: se corren con su ajuste, y el texto no las
      // vuelve a correr ni las parte.
      BorradorProducto.conTallasCorridas(borrador, tallasDelProveedor.get().ajuste());
      borrador.describirComoTenis();
      repositorioBorradores.guardar(borrador);
      return new Resolucion(TipoDeResolucion.NUEVO, borrador.alertas(), null);
    }
    Optional<AjusteDelAnuncio.PorGenero> porGenero =
        talla ? AjusteDelAnuncio.porGenero(textoDelAnuncio) : Optional.empty();
    if (porGenero.isPresent()) {
      // «Horma normal Dama, Aumenta 1 Cab»: el mismo par es la 42 de dama y la 41 de caballero.
      // Un borrador por género, cada uno con sus tallas y su ajuste (ADR-0085).
      // El título de un tenis no lleva el género: queda en la categoría (ADR-0089).
      String titulo = borrador.titulo().orElse(null);
      boolean tenis = extraido.tipo() == TipoProductoProveedor.TENIS;
      BorradorProducto caballero =
          borrador.delOtroGenero(
              tenis ? titulo : TituloPorGenero.para(titulo, "caballero"),
              porGenero.get().caballero(),
              ahora);
      if (!tenis) {
        borrador.titularPorGenero(TituloPorGenero.para(titulo, "dama"));
      }
      BorradorProducto.conGeneroDeCalzado(borrador, GeneroDeCalzado.DAMA);
      BorradorProducto.conGeneroDeCalzado(caballero, GeneroDeCalzado.CABALLERO);
      BorradorProducto.conTallasCorridas(borrador, porGenero.get().dama());
      // Cada gemelo con su género y sus tallas: el de caballero no dice «para dama» (ADR-0092).
      borrador.describirComoTenis();
      caballero.describirComoTenis();
      repositorioBorradores.guardar(borrador);
      repositorioBorradores.guardar(caballero);
      Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
      alertas.addAll(borrador.alertas());
      alertas.addAll(caballero.alertas());
      return new Resolucion(TipoDeResolucion.NUEVO, alertas, null);
    }
    // «Ojo Horma reducida pedir una talla más», «Aumentar 2 tallas», «eur»: el borrador nace con
    // las tallas que se venden y el ajuste para pedirlas (ADR-0085).
    if (talla) {
      AjusteDelAnuncio.de(textoDelAnuncio)
          .ifPresent(ajuste -> BorradorProducto.conTallasCorridas(borrador, ajuste));
    }
    // Con las tallas ya corridas: la descripción dice las que se venden (ADR-0092).
    borrador.describirComoTenis();
    repositorioBorradores.guardar(borrador);
    return new Resolucion(TipoDeResolucion.NUEVO, borrador.alertas(), null);
  }

  /**
   * Suma al borrador que ya espera revisión con la misma referencia las fotos de esta publicación
   * que son de ese producto con respaldo ({@link ExtraccionEvaluada#fotosParaSumar}) y tienen
   * archivo (10 de octubre de 2026). La camiseta 261002 de Violeta acompaña al jogger a las 10:35 y
   * a la bermuda a las 11:01: la foto de las 11:01 con su código impreso termina en el borrador de
   * las 10:35, y no perdida con la publicación descartada.
   *
   * @return cuántas se sumaron
   */
  private int sumarFotos(
      UUID borradorId, ExtraccionEvaluada evaluada, Map<UUID, MensajeProveedor> mensajes) {
    List<UUID> conArchivo =
        evaluada.fotosParaSumar().stream()
            .filter(
                id ->
                    Optional.ofNullable(mensajes.get(id))
                        .flatMap(MensajeProveedor::referenciaArchivo)
                        .isPresent())
            .toList();
    if (conArchivo.isEmpty()) {
      return 0;
    }
    Optional<BorradorProducto> destino =
        repositorioBorradores.buscarPorIdParaActualizar(borradorId);
    if (destino.isEmpty()) {
      return 0;
    }
    List<UUID> deSuPublicacion =
        repositorioPublicaciones
            .buscarPorId(destino.get().publicacionId())
            .map(PublicacionProveedor::medios)
            .orElse(List.of());
    int sumadas = destino.get().agregarFotos(conArchivo, deSuPublicacion).size();
    if (sumadas > 0) {
      repositorioBorradores.actualizar(destino.get());
    }
    return sumadas;
  }

  /**
   * ¿Hay un borrador en revisión con este mismo texto y una foto que se le parezca? Sin foto en
   * alguno de los dos lados no hay cómo saberlo, y la respuesta es no.
   */
  private boolean esAnuncioRepetido(
      Proveedor proveedor,
      PublicacionProveedor publicacion,
      String textoDelAnuncio,
      List<PHash> fotos) {
    if (fotos.isEmpty()) {
      return false;
    }
    // Uno de la misma publicación no es el anuncio otra vez: es su hermano —la otra marca, el otro
    // diseño del álbum—, y comparte las fotos que no se supo de quién eran (11 de octubre de 2026).
    return repositorioBorradores.anunciosEnRevision(proveedor.id()).stream()
        .filter(a -> !publicacion.id().equals(a.publicacionId()))
        .filter(a -> TextoDeAnuncio.mismoAnuncio(a.texto(), textoDelAnuncio))
        .anyMatch(a -> compartenFoto(a.fotos(), fotos));
  }

  /**
   * Los borradores del chat general que esperan revisión con el mismo texto de este anuncio del de
   * caballero: el general se procesó antes, y ese anuncio es de aquí.
   */
  private void rechazarLosDelChatGeneral(Proveedor proveedor, String textoDelAnuncio) {
    repositorioBorradores.anunciosEnRevision(proveedor.id()).stream()
        .filter(a -> !a.deChatDeCaballero())
        .filter(a -> TextoDeAnuncio.mismoAnuncio(a.texto(), textoDelAnuncio))
        .forEach(
            a ->
                repositorioBorradores
                    .buscarPorIdParaActualizar(a.borradorId())
                    .ifPresent(
                        borrador -> {
                          borrador.rechazar(VIENE_EN_EL_CHAT_DE_CABALLERO);
                          repositorioBorradores.actualizar(borrador);
                        }));
  }

  /** Si alguna foto de un lado está a la distancia del umbral o menos de alguna del otro. */
  private boolean compartenFoto(List<PHash> unas, List<PHash> otras) {
    return unas.stream()
        .anyMatch(u -> otras.stream().anyMatch(o -> u.distanciaHamming(o) <= umbralHamming));
  }

  /**
   * @param extraido el producto de la evaluación, o el set que se armó con él
   * @param set cómo se armó; nulo si es la prenda sola
   */
  private Resolucion renovar(
      PublicacionProveedor publicacion,
      Proveedor proveedor,
      ExtraccionEvaluada evaluada,
      ProductoExtraido extraido,
      ArmadoDeSet set,
      Producto producto,
      HuellaProveedor huella,
      PHash pHash,
      Dinero precio,
      Instant ahora) {
    Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
    alertas.addAll(evaluada.alertas());
    TipoDeResolucion tipo;
    if (extraido.estaAgotado()) {
      producto.marcarAgotadoPorProveedor(publicacion.fecha());
      tipo = TipoDeResolucion.AGOTADO;
    } else {
      producto.renovar(publicacion.fecha());
      if (precio != null && !producto.precioProveedor().map(precio::equals).orElse(false)) {
        producto.actualizarPrecioProveedor(precio);
        alertas.add(AlertaBorrador.PRECIO_CAMBIO);
      }
      tipo = TipoDeResolucion.RENOVACION;
    }
    repositorioProductos.actualizar(producto);
    // Lo que se vende bajo confirmación vuelve a la existencia nominal cuando reaparece (ADR-0089).
    if (!extraido.estaAgotado()
        && proveedor.ajustesDeIngesta().ventaBajoConfirmacion()
        && existenciaBajoConfirmacion != null) {
      existenciaBajoConfirmacion.reponer(producto);
    }
    // El de caballero de un anuncio unisex vive y se agota con el de dama: es el mismo par en otra
    // talla (ADR-0085). Sin esto, un «AGOTADO» dejaba a la venta al otro sin existencias.
    gemeloDe(proveedor.id(), producto)
        .ifPresent(
            gemelo -> {
              if (extraido.estaAgotado()) {
                gemelo.marcarAgotadoPorProveedor(publicacion.fecha());
              } else {
                gemelo.renovar(publicacion.fecha());
                if (precio != null && !gemelo.precioProveedor().map(precio::equals).orElse(false)) {
                  gemelo.actualizarPrecioProveedor(precio);
                }
              }
              repositorioProductos.actualizar(gemelo);
            });
    repositorioBorradores.guardar(
        BorradorProducto.conSet(
            BorradorProducto.renovacionAplicada(
                publicacion.id(),
                proveedor.id(),
                producto.id(),
                extraido,
                evaluada.jsonCrudo(),
                precio,
                huella,
                pHash,
                alertas,
                ahora),
            set));
    return new Resolucion(tipo, alertas, null);
  }

  /**
   * El anuncio de SKU que son prendas de un álbum ya aprobado (13 de octubre de 2026): el álbum es
   * un solo producto y cada SKU quedó en sus variantes. Agotado, esas variantes dejan de venderse y
   * las demás siguen; si no queda ninguna a la venta, se agota el producto. Si no viene agotado,
   * las prendas vuelven a la venta y el producto se renueva.
   *
   * <p><b>El costo no se toca</b>: el anuncio de un SKU puede ser la prenda sola de un álbum que se
   * aprobó como set, o al revés, y renovarle el costo con el de la otra clase lo dejaría mal. Un
   * cambio de precio del álbum entero lo trae el álbum, que se reconoce por su foto. <b>Un anuncio
   * más viejo que el último visto del producto no cambia nada</b>, como en el producto: dos lotes
   * fuera de orden no pueden volver a poner a la venta lo que ya se agotó.
   *
   * @param referencias el SKU del anuncio, o los del álbum que se vuelve a publicar
   * @return vacío si ningún SKU es de una prenda
   */
  private Optional<Resolucion> resolverPrendas(
      PublicacionProveedor publicacion,
      Proveedor proveedor,
      ExtraccionEvaluada evaluada,
      ProductoExtraido extraido,
      ArmadoDeSet set,
      List<String> referencias,
      HuellaProveedor huella,
      PHash pHash,
      Dinero precio,
      Instant ahora) {
    Map<UUID, Producto> productos = new LinkedHashMap<>();
    Map<UUID, List<PrendaDeProveedor>> prendasPorProducto = new LinkedHashMap<>();
    for (String referencia : referencias) {
      for (PrendaDeProveedor prenda :
          productosDeProveedor.buscarPrendasPorReferencia(proveedor.id(), referencia)) {
        Optional<Producto> producto =
            Optional.ofNullable(productos.get(prenda.productoId()))
                .or(() -> repositorioProductos.buscarPorId(prenda.productoId()));
        if (producto.isEmpty()) {
          continue;
        }
        productos.put(prenda.productoId(), producto.get());
        prendasPorProducto
            .computeIfAbsent(prenda.productoId(), id -> new ArrayList<>())
            .add(prenda);
      }
    }
    if (productos.isEmpty()) {
      return Optional.empty();
    }
    for (Producto producto : productos.values()) {
      boolean esViejo =
          producto.vistoPorUltimaVez().map(publicacion.fecha()::isBefore).orElse(false);
      List<PrendaDeProveedor> suyas = prendasPorProducto.get(producto.id());
      if (extraido.estaAgotado()) {
        if (esViejo) {
          continue;
        }
        List<UUID> variantes = suyas.stream().flatMap(p -> p.variantes().stream()).toList();
        productosDeProveedor.cambiarDisponibilidadDeVariantes(variantes, false);
        // Lo que queda a la venta: todo lo del producto que se vendía, menos estas prendas.
        PrendaDeProveedor primera = suyas.getFirst();
        int seVendian =
            primera.otrasALaVenta() + (primera.retirada() ? 0 : primera.variantes().size());
        int seAgotan =
            suyas.stream().filter(pr -> !pr.retirada()).mapToInt(pr -> pr.variantes().size()).sum();
        if (seVendian - seAgotan <= 0) {
          producto.marcarAgotadoPorProveedor(publicacion.fecha());
        }
      } else {
        if (!esViejo) {
          List<UUID> retiradas =
              suyas.stream()
                  .filter(PrendaDeProveedor::retirada)
                  .flatMap(p -> p.variantes().stream())
                  .toList();
          if (!retiradas.isEmpty()) {
            productosDeProveedor.cambiarDisponibilidadDeVariantes(retiradas, true);
          }
        }
        producto.renovar(publicacion.fecha());
      }
      repositorioProductos.actualizar(producto);
    }
    Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
    alertas.addAll(evaluada.alertas());
    repositorioBorradores.guardar(
        BorradorProducto.conSet(
            BorradorProducto.renovacionAplicada(
                publicacion.id(),
                proveedor.id(),
                productos.keySet().iterator().next(),
                extraido,
                evaluada.jsonCrudo(),
                precio,
                huella,
                pHash,
                alertas,
                ahora),
            set));
    return Optional.of(
        new Resolucion(
            extraido.estaAgotado() ? TipoDeResolucion.AGOTADO : TipoDeResolucion.RENOVACION,
            alertas,
            null));
  }

  private Resolucion descartar(PublicacionProveedor publicacion, CierreDePublicacion motivo) {
    publicacion.descartar(motivo);
    repositorioPublicaciones.actualizar(publicacion);
    return Resolucion.descartada(motivo);
  }

  /**
   * El precio de la prenda para sumarle su acompañante. El del texto es el primero que el anuncio
   * escribe, y en «Básica 48.000 / Falda 59.900» ese es el de la blusa: si el extractor leyó otro y
   * ese sí está escrito, manda el del extractor. La alerta de precio inconsistente queda igual.
   */
  private static Dinero precioDeLaPrenda(
      ProductoExtraido extraido, Dinero delTexto, String textoDelAnuncio) {
    return extraido
        .precioProveedorOpcional()
        .filter(p -> !p.equals(delTexto) && ArmadorDeSets.estaEscrito(p, textoDelAnuncio))
        .orElse(delTexto);
  }

  /**
   * ¿Cuesta la prenda más de lo que se paga por su tipo? Con el precio de la prenda sola —el mismo
   * con que se armaría el set ({@link #precioDeLaPrenda}): en «Básica 48.000 / Pantalón 95.000» el
   * primero del texto es el de la blusa—, y nunca en un agotado ({@link TopeDeCostoPorTipo}).
   */
  private static boolean pasaDelTope(ExtraccionEvaluada evaluada, String textoDelAnuncio) {
    return !evaluada.producto().estaAgotado()
        && TopeDeCostoPorTipo.loExcede(
            evaluada.producto().tipo(),
            precioDeLaPrenda(evaluada.producto(), evaluada.precioProveedor(), textoDelAnuncio));
  }

  private static Set<AlertaBorrador> conLasDelSet(
      Set<AlertaBorrador> deLaEvaluacion, Set<AlertaBorrador> delSet) {
    if (delSet.isEmpty()) {
      return deLaEvaluacion;
    }
    Set<AlertaBorrador> todas = EnumSet.noneOf(AlertaBorrador.class);
    todas.addAll(deLaEvaluacion);
    todas.addAll(delSet);
    return todas;
  }

  /**
   * Los sets que se armaron con esta misma prenda, con gorra o con blusa: por la huella de la
   * prenda con cada acompañante, o por la foto entre los sets.
   */
  private List<Producto> setsDeLaPrenda(
      UUID proveedorId, HuellaProveedor huella, PHash pHash, List<HuellaVisual> huellasVisuales) {
    List<Producto> sets = new ArrayList<>();
    for (PrendaAcompanante acompanante :
        List.of(PrendaAcompanante.GORRA, PrendaAcompanante.BLUSA)) {
      buscarExistente(
              proveedorId,
              huella == null ? null : HuellaProveedor.deSet(huella, acompanante),
              pHash,
              huellasVisuales,
              acompanante)
          .filter(p -> sets.stream().noneMatch(s -> s.id().equals(p.id())))
          .ifPresent(sets::add);
    }
    return sets;
  }

  /**
   * @param acompanante el del set que se busca; nulo para la prenda sola. Por la foto solo se
   *     reconocen los productos de esa misma clase.
   */
  private Optional<Producto> buscarExistente(
      UUID proveedorId,
      HuellaProveedor huella,
      PHash pHash,
      List<HuellaVisual> huellasVisuales,
      PrendaAcompanante acompanante) {
    if (huella != null) {
      Optional<Producto> porHuella = productosDeProveedor.buscarPorHuella(proveedorId, huella);
      if (porHuella.isPresent()) {
        return porHuella;
      }
    }
    if (pHash == null) {
      return Optional.empty();
    }
    List<Producto> porFoto =
        huellasVisuales.stream()
            .filter(h -> h.acompananteDelSet() == acompanante)
            .filter(h -> h.pHash().distanciaHamming(pHash) <= umbralHamming)
            .map(HuellaVisual::productoId)
            .distinct()
            .map(repositorioProductos::buscarPorId)
            .flatMap(Optional::stream)
            .toList();
    // El de dama y el de caballero de un anuncio unisex comparten foto: manda el de dama, y la
    // renovación lleva consigo al otro ({@link #gemeloDe}). Sin preferencia, la consulta no tiene
    // orden y caía en cualquiera de los dos.
    return porFoto.stream()
        .filter(
            p ->
                p.huellaProveedor()
                    .map(HuellaProveedor::delGemelo)
                    .map(
                        g ->
                            porFoto.stream()
                                .anyMatch(o -> o.huellaProveedor().equals(Optional.of(g))))
                    .orElse(false))
        .findFirst()
        .or(() -> porFoto.stream().findFirst());
  }

  /** El de caballero de un anuncio unisex aprobado como dos productos (ADR-0085), si existe. */
  private Optional<Producto> gemeloDe(UUID proveedorId, Producto producto) {
    return producto
        .huellaProveedor()
        .map(HuellaProveedor::delGemelo)
        .flatMap(g -> productosDeProveedor.buscarPorHuella(proveedorId, g))
        .filter(g -> !g.id().equals(producto.id()));
  }

  /**
   * Las huellas visuales de un producto, la de su principal primero. Con un solo producto, las de
   * sus fotos; con varios, solo si la extracción le repartió una exclusiva: la primera foto del
   * mensaje puede ser de cualquiera de ellos, y con ella el jean del conjunto se reconocería
   * después como la chaqueta.
   */
  private static List<PHash> fotosDe(
      PublicacionProveedor publicacion,
      ExtraccionEvaluada evaluada,
      int cuantas,
      Map<UUID, PHash> pHashes) {
    if (cuantas > 1 && evaluada.exclusivaOpcional().isEmpty()) {
      return List.of();
    }
    List<UUID> propias = evaluada.fotos().isEmpty() ? publicacion.medios() : evaluada.fotos();
    List<PHash> fotos = new ArrayList<>();
    evaluada.exclusivaOpcional().map(pHashes::get).ifPresent(fotos::add);
    propias.stream()
        .filter(id -> !evaluada.exclusivaOpcional().map(id::equals).orElse(false))
        .map(pHashes::get)
        .filter(Objects::nonNull)
        .forEach(fotos::add);
    return fotos;
  }

  /** Si alguna evaluación va a usar las huellas visuales de sus fotos. */
  public static boolean necesitaPHashes(List<ExtraccionEvaluada> evaluadas) {
    return evaluadas.size() == 1
        || evaluadas.stream().anyMatch(e -> e.exclusivaOpcional().isPresent());
  }

  /** Las huellas visuales contra las que se compara cada foto del lote. Una consulta por lote. */
  public List<HuellaVisual> huellasVisualesDe(UUID proveedorId) {
    return repositorioBorradores.huellasVisualesDelProveedor(proveedorId);
  }

  /**
   * El pHash de cada foto legible de la publicación, por mensaje y en orden: la primera es la
   * principal, y las demás sirven para reconocer el mismo anuncio aunque las fotos lleguen en otro
   * orden. Lee los archivos del bucket y los decodifica, así que va fuera de cualquier transacción.
   */
  public Map<UUID, PHash> pHashesDe(
      PublicacionProveedor publicacion, Map<UUID, MensajeProveedor> mensajes) {
    Map<UUID, PHash> pHashes = new LinkedHashMap<>();
    publicacion.medios().stream()
        .map(mensajes::get)
        .filter(Objects::nonNull)
        .filter(m -> m.referenciaArchivo().isPresent())
        .forEach(m -> pHashDe(m).ifPresent(p -> pHashes.put(m.id(), p)));
    return pHashes;
  }

  /** El que se guardó al registrar el mensaje; si no hay, el de leer la foto del bucket. */
  private Optional<PHash> pHashDe(MensajeProveedor mensaje) {
    if (mensaje.pHash().isPresent()) {
      return mensaje.pHash();
    }
    return mensaje.referenciaArchivo().flatMap(almacen::leer).flatMap(calculadorDePHash::de);
  }

  /**
   * El del proveedor si lo tiene; si no, el de la línea que dijo el extractor, o la del proveedor.
   */
  private BigDecimal factorDe(Proveedor proveedor, ProductoExtraido extraido) {
    return proveedor
        .factorDeMargen()
        .orElseGet(
            () -> {
              LineaCatalogo linea = extraido.lineaOpcional().orElse(proveedor.linea());
              BigDecimal factor = factorPorLinea.get(linea);
              if (factor == null) {
                factor = factorPorLinea.get(proveedor.linea());
              }
              if (factor == null) {
                throw new IllegalStateException(
                    "No hay factor de margen configurado para la línea " + proveedor.linea() + ".");
              }
              return factor;
            });
  }

  public enum TipoDeResolucion {
    NUEVO,
    RENOVACION,
    AGOTADO,
    DESCARTADA
  }

  /**
   * Qué se decidió y con qué alertas, para que el lote las cuente.
   *
   * @param cierre por qué se descartó; nulo en lo demás
   */
  public record Resolucion(
      TipoDeResolucion tipo, Set<AlertaBorrador> alertas, CierreDePublicacion cierre) {
    public Resolucion {
      alertas = Set.copyOf(alertas);
    }

    static Resolucion descartada(CierreDePublicacion cierre) {
      return new Resolucion(TipoDeResolucion.DESCARTADA, Set.of(), cierre);
    }
  }
}
