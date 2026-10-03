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
import co.tecnosport.api.domain.proveedores.TopesDeGanancia;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * <p>Primero por la huella —proveedor, título normalizado y precio—, que es exacta. Si no cae, por
 * la foto: el pHash de la primera foto contra los de los productos del mismo proveedor, dentro del
 * umbral de Hamming. <b>La foto reconoce aunque el precio cambie</b>, y ahí es donde nace {@code
 * PRECIO_CAMBIO}: la huella no puede dar ese aviso porque lleva el precio dentro.
 *
 * <p>Una renovación no crea un borrador para revisar: actualiza la última vista, reactiva el
 * producto si estaba oculto y deja una constancia {@code RENOVACION_APLICADA}. Un agotado sobre un
 * producto existente lo agota de inmediato. Un agotado sobre algo que no está en el catálogo no es
 * nada que publicar, y se descarta con ese motivo.
 */
public final class ResolverBorrador {

  private static final String SIN_PRODUCTO = "El extractor no reconoció un producto en el mensaje.";

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
   * La forma cómoda: calcula el pHash y carga las huellas visuales aquí mismo. Lee del bucket y
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
        evaluadas.size() == 1 ? pHashDe(publicacion, mensajes).orElse(null) : null,
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
   * @param pHash el de la primera foto de la publicación, ya calculado fuera de la transacción, o
   *     nulo si no hay foto legible
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
      PHash pHash,
      List<HuellaVisual> huellasVisuales) {
    if (evaluadas.isEmpty()) {
      return List.of(descartar(publicacion, SIN_PRODUCTO));
    }
    PHash pHashUsable = evaluadas.size() == 1 ? pHash : null;
    List<Resolucion> resoluciones = new ArrayList<>(evaluadas.size());
    for (ExtraccionEvaluada evaluada : evaluadas) {
      resoluciones.add(resolverUno(publicacion, proveedor, evaluada, pHashUsable, huellasVisuales));
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
      PHash pHash,
      List<HuellaVisual> huellasVisuales) {
    ProductoExtraido extraido = evaluada.producto();
    if (!extraido.esProducto()) {
      return Resolucion.descartada(SIN_PRODUCTO);
    }

    Dinero precio = evaluada.precioProveedor();
    HuellaProveedor huella =
        extraido.tituloOpcional().isPresent() && precio != null
            ? HuellaProveedor.calcular(proveedor.id(), extraido.titulo(), precio)
            : null;
    Instant ahora = reloj.ahora();

    Optional<Producto> existente = buscarExistente(proveedor.id(), huella, pHash, huellasVisuales);
    if (existente.isPresent()) {
      return renovar(
          publicacion, proveedor, evaluada, existente.get(), huella, pHash, precio, ahora);
    }
    if (extraido.estaAgotado()) {
      return Resolucion.descartada("Anuncia como agotado un producto que no está en el catálogo.");
    }
    if (huella != null && repositorioBorradores.existeEnRevisionConHuella(proveedor.id(), huella)) {
      // El mismo anuncio repetido antes de que alguien apruebe el primero. Un segundo borrador
      // solo sirve para chocar con el índice único al aprobarlo.
      return Resolucion.descartada("Es el mismo anuncio de un borrador que ya está en revisión.");
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
            ahora);
    repositorioBorradores.guardar(borrador);
    return new Resolucion(TipoDeResolucion.NUEVO, borrador.alertas(), null);
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

  /** Las huellas visuales contra las que se compara cada foto del lote. Una consulta por lote. */
  public List<HuellaVisual> huellasVisualesDe(UUID proveedorId) {
    return repositorioBorradores.huellasVisualesDelProveedor(proveedorId);
  }

  /**
   * El pHash de la primera foto de la publicación: lee el archivo del bucket y lo decodifica, así
   * que va fuera de cualquier transacción.
   */
  public Optional<PHash> pHashDe(
      PublicacionProveedor publicacion, Map<UUID, MensajeProveedor> mensajes) {
    return publicacion.medios().stream()
        .map(mensajes::get)
        .filter(Objects::nonNull)
        .map(m -> m.referenciaArchivo().orElse(null))
        .filter(Objects::nonNull)
        .findFirst()
        .flatMap(almacen::leer)
        .flatMap(calculadorDePHash::de);
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
