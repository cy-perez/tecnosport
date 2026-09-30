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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
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

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final CalculadorDePHash calculadorDePHash;
  private final Reloj reloj;
  private final Map<LineaCatalogo, BigDecimal> factorPorLinea;
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
      int umbralHamming) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.almacen = Objects.requireNonNull(almacen);
    this.calculadorDePHash = Objects.requireNonNull(calculadorDePHash);
    this.reloj = Objects.requireNonNull(reloj);
    this.factorPorLinea = Map.copyOf(Objects.requireNonNull(factorPorLinea));
    if (umbralHamming < 0) {
      throw new IllegalArgumentException("El umbral de Hamming no puede ser negativo.");
    }
    this.umbralHamming = umbralHamming;
  }

  public Resolucion ejecutar(
      PublicacionProveedor publicacion,
      Map<UUID, MensajeProveedor> mensajes,
      Proveedor proveedor,
      ExtraccionEvaluada evaluada) {
    ProductoExtraido extraido = evaluada.producto();
    if (!extraido.esProducto()) {
      return descartar(publicacion, "El extractor no reconoció un producto en el mensaje.");
    }

    Dinero precio = evaluada.precioProveedor();
    HuellaProveedor huella =
        extraido.tituloOpcional().isPresent() && precio != null
            ? HuellaProveedor.calcular(proveedor.id(), extraido.titulo(), precio)
            : null;
    PHash pHash = pHashDeLaPrimeraFoto(publicacion, mensajes).orElse(null);
    Instant ahora = reloj.ahora();

    Optional<Producto> existente = buscarExistente(proveedor.id(), huella, pHash);
    if (existente.isPresent()) {
      return renovar(
          publicacion, proveedor, evaluada, existente.get(), huella, pHash, precio, ahora);
    }
    if (extraido.estaAgotado()) {
      return descartar(publicacion, "Anuncia como agotado un producto que no está en el catálogo.");
    }

    Dinero precioSugerido =
        precio == null ? null : CalculadoraDeMargen.sugerir(precio, factorDe(proveedor, extraido));
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
    publicacion.marcarExtraida();
    repositorioPublicaciones.actualizar(publicacion);
    return new Resolucion(TipoDeResolucion.NUEVO, borrador.alertas());
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
    publicacion.marcarExtraida();
    repositorioPublicaciones.actualizar(publicacion);
    return new Resolucion(tipo, alertas);
  }

  private Resolucion descartar(PublicacionProveedor publicacion, String motivo) {
    publicacion.descartar(motivo);
    repositorioPublicaciones.actualizar(publicacion);
    return new Resolucion(TipoDeResolucion.DESCARTADA, Set.of());
  }

  private Optional<Producto> buscarExistente(
      UUID proveedorId, HuellaProveedor huella, PHash pHash) {
    if (huella != null) {
      Optional<Producto> porHuella = productosDeProveedor.buscarPorHuella(proveedorId, huella);
      if (porHuella.isPresent()) {
        return porHuella;
      }
    }
    if (pHash == null) {
      return Optional.empty();
    }
    return repositorioBorradores.huellasVisualesDelProveedor(proveedorId).stream()
        .filter(h -> h.pHash().distanciaHamming(pHash) <= umbralHamming)
        .findFirst()
        .flatMap(h -> repositorioProductos.buscarPorId(h.productoId()));
  }

  private Optional<PHash> pHashDeLaPrimeraFoto(
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

  /** Qué se decidió y con qué alertas, para que el lote las cuente. */
  public record Resolucion(TipoDeResolucion tipo, Set<AlertaBorrador> alertas) {
    public Resolucion {
      alertas = Set.copyOf(alertas);
    }
  }
}
