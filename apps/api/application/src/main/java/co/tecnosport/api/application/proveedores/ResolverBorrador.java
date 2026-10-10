package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.CalculadoraDeMargen;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TextoDeAnuncio;
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
import java.util.stream.Collectors;

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
 * <p>Una renovación no crea un borrador para revisar: actualiza la última vista, reactiva el
 * producto si estaba oculto y deja una constancia {@code RENOVACION_APLICADA}. Un agotado sobre un
 * producto existente lo agota de inmediato. Un agotado sobre algo que no está en el catálogo no es
 * nada que publicar, y se descarta con ese motivo.
 */
public final class ResolverBorrador {

  private static final String SIN_PRODUCTO = "El extractor no reconoció un producto en el mensaje.";

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
    List<Resolucion> resoluciones = new ArrayList<>(evaluadas.size());
    boolean vieneEnElDeCaballero =
        !chat.deCaballero()
            && chat.textosDeCaballero().stream()
                .anyMatch(t -> TextoDeAnuncio.mismoAnuncio(t, textoDelAnuncio));
    for (ExtraccionEvaluada evaluada : evaluadas) {
      resoluciones.add(
          vieneEnElDeCaballero
              ? Resolucion.descartada(VIENE_EN_EL_CHAT_DE_CABALLERO)
              : resolverUno(
                  publicacion,
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
      publicacion.descartar(
          resoluciones.stream()
              .map(Resolucion::motivo)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.joining(" ")));
    }
    repositorioPublicaciones.actualizar(publicacion);
    return resoluciones;
  }

  private Resolucion resolverUno(
      PublicacionProveedor publicacion,
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
    PHash pHash = fotos.isEmpty() ? null : fotos.getFirst();
    Instant ahora = reloj.ahora();

    Optional<Producto> existente = buscarExistente(proveedor.id(), huella, pHash, huellasVisuales);
    if (existente.isPresent()) {
      return renovar(
          publicacion, proveedor, evaluada, existente.get(), huella, pHash, precio, ahora);
    }
    if (extraido.estaAgotado()) {
      return Resolucion.descartada("Anuncia como agotado un producto que no está en el catálogo.");
    }
    if (codigo.isPresent()
        && repositorioBorradores.existeEnRevisionConHuella(proveedor.id(), huella)) {
      // La misma referencia antes de que alguien apruebe la primera. Un segundo borrador solo
      // sirve para chocar con el índice único al aprobarlo.
      return Resolucion.descartada(
          "Es la misma referencia de un borrador que ya está en revisión.");
    }
    if (codigo.isEmpty() && esAnuncioRepetido(proveedor, textoDelAnuncio, fotos)) {
      return Resolucion.descartada(
          "Es el mismo anuncio, con la misma foto, de un borrador que ya está en revisión.");
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
            evaluada.alertas(),
            evaluada.reparto(),
            ahora);
    repositorioBorradores.guardar(borrador);
    return new Resolucion(TipoDeResolucion.NUEVO, borrador.alertas(), null);
  }

  /**
   * ¿Hay un borrador en revisión con este mismo texto y una foto que se le parezca? Sin foto en
   * alguno de los dos lados no hay cómo saberlo, y la respuesta es no.
   */
  private boolean esAnuncioRepetido(
      Proveedor proveedor, String textoDelAnuncio, List<PHash> fotos) {
    if (fotos.isEmpty()) {
      return false;
    }
    return repositorioBorradores.anunciosEnRevision(proveedor.id()).stream()
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

  private Resolucion renovar(
      PublicacionProveedor publicacion,
      Proveedor proveedor,
      ExtraccionEvaluada evaluada,
      Producto producto,
      HuellaProveedor huella,
      PHash pHash,
      Dinero precio,
      Instant ahora) {
    Set<AlertaBorrador> alertas = EnumSet.noneOf(AlertaBorrador.class);
    alertas.addAll(evaluada.alertas());
    TipoDeResolucion tipo;
    if (evaluada.producto().estaAgotado()) {
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
    repositorioBorradores.guardar(
        BorradorProducto.renovacionAplicada(
            publicacion.id(),
            proveedor.id(),
            producto.id(),
            evaluada.producto(),
            evaluada.jsonCrudo(),
            precio,
            huella,
            pHash,
            alertas,
            ahora));
    return new Resolucion(tipo, alertas, null);
  }

  private Resolucion descartar(PublicacionProveedor publicacion, String motivo) {
    publicacion.descartar(motivo);
    repositorioPublicaciones.actualizar(publicacion);
    return Resolucion.descartada(motivo);
  }

  private Optional<Producto> buscarExistente(
      UUID proveedorId, HuellaProveedor huella, PHash pHash, List<HuellaVisual> huellasVisuales) {
    if (huella != null) {
      Optional<Producto> porHuella = productosDeProveedor.buscarPorHuella(proveedorId, huella);
      if (porHuella.isPresent()) {
        return porHuella;
      }
    }
    if (pHash == null) {
      return Optional.empty();
    }
    return huellasVisuales.stream()
        .filter(h -> h.pHash().distanciaHamming(pHash) <= umbralHamming)
        .findFirst()
        .flatMap(h -> repositorioProductos.buscarPorId(h.productoId()));
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
   * @param motivo por qué se descartó; nulo en lo demás
   */
  public record Resolucion(TipoDeResolucion tipo, Set<AlertaBorrador> alertas, String motivo) {
    public Resolucion {
      alertas = Set.copyOf(alertas);
    }

    static Resolucion descartada(String motivo) {
      return new Resolucion(TipoDeResolucion.DESCARTADA, Set.of(), motivo);
    }
  }
}
