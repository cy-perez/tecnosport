// Sin exigir productoId en APROBADO: nace con él, pero el producto se puede borrar del catálogo
// después y la constancia tiene que poder releerse sin él. Mismo criterio que BorradorProducto.
package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un modelo de tecnología de la lista del proveedor, esperando a que una persona elija qué colores
 * se venden de cada configuración y a qué precio.
 *
 * <p>No es un {@link BorradorProducto}: aquel sale de una publicación del chat con fotos, tallas y
 * tonos que extrajo un modelo de lenguaje; este sale de una lista de precios que ya procesó la
 * skill, y lo que falta decidir es otra cosa. Comparten el destino —un {@code Producto} de
 * proveedor— y los estados.
 *
 * <h2>Dos clases de borrador con la misma forma</h2>
 *
 * <ul>
 *   <li><b>De un modelo nuevo</b> ({@link #productoId()} vacío mientras está en revisión): al
 *       aprobarlo nace el producto, con una variante por color elegido de cada configuración.
 *   <li><b>De configuraciones nuevas de un modelo que ya se vende</b> ({@link #productoId()}
 *       presente desde que nace): al aprobarlo, las variantes se añaden a ese producto.
 * </ul>
 *
 * <p>Una configuración sin colores elegidos no se vende, y eso no es un error: el proveedor puede
 * ofrecer una memoria que no interesa. Lo que no se puede es aprobar sin vender ninguna.
 */
public final class BorradorTecnologia {

  private final UUID id;
  private final UUID proveedorId;
  private ModeloDeLista modelo;
  private final HuellaProveedor huella;
  private final List<ConfiguracionTecnologia> configuraciones;
  private UUID productoId;
  private EstadoBorrador estado;
  private String motivoRechazo;
  private Instant vistoEn;
  private final Instant creadoEn;

  public BorradorTecnologia(
      UUID id,
      UUID proveedorId,
      ModeloDeLista modelo,
      List<ConfiguracionTecnologia> configuraciones,
      UUID productoId,
      EstadoBorrador estado,
      String motivoRechazo,
      Instant vistoEn,
      Instant creadoEn) {
    this.id = Objects.requireNonNull(id, "El id del borrador no puede ser nulo.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un borrador es de un proveedor.");
    this.modelo = Objects.requireNonNull(modelo, "Un borrador de tecnología es de un modelo.");
    this.huella = HuellaProveedor.deModelo(proveedorId, modelo.idModelo());
    this.configuraciones = new ArrayList<>(configuracionesValidas(configuraciones));
    this.productoId = productoId;
    this.estado = Objects.requireNonNull(estado, "El estado del borrador no puede ser nulo.");
    if (estado == EstadoBorrador.RENOVACION_APLICADA) {
      throw new ExcepcionDeDominio("Un borrador de tecnología no es una constancia de renovación.");
    }
    this.motivoRechazo = enBlancoEsNulo(motivoRechazo);
    this.vistoEn = Objects.requireNonNull(vistoEn, "El borrador dice de qué lista salió.");
    this.creadoEn = Objects.requireNonNull(creadoEn, "El borrador tiene fecha.");
    if (estado == EstadoBorrador.RECHAZADO && this.motivoRechazo == null) {
      throw new ExcepcionDeDominio("Un borrador rechazado dice por qué.");
    }
  }

  /**
   * @param productoExistente el producto del modelo si ya se vende, y entonces el borrador es de
   *     las configuraciones que le faltan; nulo si el modelo es nuevo
   */
  public static BorradorTecnologia nuevo(
      UUID proveedorId,
      ModeloDeLista modelo,
      List<ConfiguracionTecnologia> configuraciones,
      UUID productoExistente,
      Instant vistoEn,
      Instant ahora) {
    return new BorradorTecnologia(
        GeneradorIdentificador.nuevo(),
        proveedorId,
        modelo,
        configuraciones,
        productoExistente,
        EstadoBorrador.EN_REVISION,
        null,
        vistoEn,
        ahora);
  }

  /**
   * Llegó otra lista con este modelo antes de que nadie lo revisara: manda la de hoy —costos,
   * configuraciones, colores sugeridos, descripción—, y lo que ya se había elegido sobre una
   * configuración que sigue en la lista se conserva. Lo elegido sobre una que ya no viene se pierde
   * con ella. Una lista más vieja que la última vista no cambia nada.
   */
  public void actualizarConLista(
      ModeloDeLista deHoy, List<ConfiguracionTecnologia> deLaLista, Instant visto) {
    exigirEnRevision("actualizar");
    Objects.requireNonNull(visto, "La fecha de la lista no puede ser nula.");
    if (visto.isBefore(vistoEn)) {
      return;
    }
    if (!deHoy.idModelo().equals(modelo.idModelo())) {
      throw new ExcepcionDeDominio("Una lista no puede cambiarle el modelo a un borrador.");
    }
    Map<String, ConfiguracionTecnologia> anteriores = porSku(configuraciones);
    List<ConfiguracionTecnologia> nuevas = new ArrayList<>();
    for (ConfiguracionTecnologia hoy : configuracionesValidas(deLaLista)) {
      ConfiguracionTecnologia anterior = anteriores.get(hoy.sku());
      nuevas.add(anterior == null ? hoy : hoy.conLaEleccionDe(anterior));
    }
    this.modelo = deHoy;
    this.configuraciones.clear();
    this.configuraciones.addAll(nuevas);
    this.vistoEn = visto;
  }

  /** Lo que quien revisa decide de una configuración. */
  public record Eleccion(String sku, List<String> colores, Dinero precioVenta) {
    public Eleccion {
      Objects.requireNonNull(sku, "La elección dice de qué configuración es.");
      colores = colores == null ? List.of() : List.copyOf(colores);
    }
  }

  /**
   * Fija los colores y el precio de las configuraciones que se nombran; las demás quedan como
   * estaban. Con paleta oficial, un color tiene que ser de ella: es la que dice qué colores existen
   * de verdad, y un nombre escrito a mano dejaría una variante que nadie puede reconocer en la
   * siguiente lista. Sin paleta, se escribe a mano.
   */
  public void elegir(List<Eleccion> elecciones) {
    exigirEnRevision("editar");
    Map<String, ConfiguracionTecnologia> actuales = porSku(configuraciones);
    for (Eleccion eleccion : elecciones) {
      ConfiguracionTecnologia actual = actuales.get(eleccion.sku());
      if (actual == null) {
        throw new ExcepcionDeDominio(
            "La configuración '" + eleccion.sku() + "' no es de este borrador.");
      }
      List<String> colores = ConfiguracionTecnologia.limpiar(eleccion.colores());
      if (!modelo.paleta().isEmpty()) {
        for (String color : colores) {
          if (!modelo.paleta().contains(color)) {
            throw new ExcepcionDeDominio(
                "El color '" + color + "' no está en la paleta de " + modelo.titulo() + ".");
          }
        }
      }
      if (eleccion.precioVenta() != null && eleccion.precioVenta().valor().signum() == 0) {
        throw new ExcepcionDeDominio("El precio de venta no puede ser cero.");
      }
      actuales.put(actual.sku(), actual.elegir(colores, eleccion.precioVenta()));
    }
    this.configuraciones.replaceAll(c -> actuales.get(c.sku()));
  }

  /**
   * @param productoId el producto que nació, o el que ya existía y recibió las variantes
   */
  public void aprobar(UUID productoId) {
    exigirEnRevision("aprobar");
    exigirAprobable();
    this.productoId =
        Objects.requireNonNull(productoId, "Aprobar es crear o completar un producto.");
    this.estado = EstadoBorrador.APROBADO;
  }

  /**
   * Lo que impide aprobar, dicho antes de tocar el catálogo: ninguna configuración con colores, o
   * una con colores y sin precio.
   */
  public void exigirAprobable() {
    List<ConfiguracionTecnologia> vendibles = configuracionesQueSeVenden();
    if (vendibles.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Elige los colores de al menos una configuración: sin eso no hay nada que vender.");
    }
    for (ConfiguracionTecnologia c : vendibles) {
      if (c.precioVenta() == null) {
        throw new ExcepcionDeDominio("Falta el precio de venta de " + c.titulo() + ".");
      }
    }
  }

  public void rechazar(String motivo) {
    exigirEnRevision("rechazar");
    String porQue = enBlancoEsNulo(motivo);
    if (porQue == null) {
      throw new ExcepcionDeDominio("Rechazar un borrador exige el motivo.");
    }
    this.estado = EstadoBorrador.RECHAZADO;
    this.motivoRechazo = porQue;
  }

  /**
   * Las configuraciones que una persona ya decidió no vender: todas las de un borrador rechazado y
   * las que quedaron sin colores en uno aprobado. La siguiente lista no las vuelve a proponer; una
   * configuración que no esté aquí y el producto no tenga, sí.
   */
  public Set<String> skusDescartados() {
    Set<String> descartados = new LinkedHashSet<>();
    for (ConfiguracionTecnologia c : configuraciones) {
      if (estado == EstadoBorrador.RECHAZADO
          || (estado == EstadoBorrador.APROBADO && !c.seVende())) {
        descartados.add(c.sku());
      }
    }
    return descartados;
  }

  public List<ConfiguracionTecnologia> configuracionesQueSeVenden() {
    return configuraciones.stream().filter(ConfiguracionTecnologia::seVende).toList();
  }

  /** El costo más bajo de lo que se vende: el precio de proveedor que guarda el producto. */
  public Dinero costoMinimoDeLoQueSeVende() {
    return configuracionesQueSeVenden().stream()
        .map(ConfiguracionTecnologia::costoProveedor)
        .min((a, b) -> a.valor().compareTo(b.valor()))
        .orElseThrow(() -> new ExcepcionDeDominio("No hay ninguna configuración que se venda."));
  }

  private void exigirEnRevision(String accion) {
    if (estado != EstadoBorrador.EN_REVISION) {
      throw new ExcepcionDeDominio(
          "Solo se puede " + accion + " un borrador en revisión; este está " + estado + ".");
    }
  }

  private static List<ConfiguracionTecnologia> configuracionesValidas(
      List<ConfiguracionTecnologia> configuraciones) {
    if (configuraciones == null || configuraciones.isEmpty()) {
      throw new ExcepcionDeDominio("Un borrador de tecnología tiene al menos una configuración.");
    }
    if (porSku(configuraciones).size() != configuraciones.size()) {
      throw new ExcepcionDeDominio("Una configuración aparece dos veces en el mismo borrador.");
    }
    return configuraciones;
  }

  private static Map<String, ConfiguracionTecnologia> porSku(
      List<ConfiguracionTecnologia> configuraciones) {
    Map<String, ConfiguracionTecnologia> mapa = new LinkedHashMap<>();
    for (ConfiguracionTecnologia c : configuraciones) {
      mapa.put(c.sku(), c);
    }
    return mapa;
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  public UUID id() {
    return id;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  public ModeloDeLista modelo() {
    return modelo;
  }

  public HuellaProveedor huella() {
    return huella;
  }

  public List<ConfiguracionTecnologia> configuraciones() {
    return List.copyOf(configuraciones);
  }

  public Optional<UUID> productoId() {
    return Optional.ofNullable(productoId);
  }

  public EstadoBorrador estado() {
    return estado;
  }

  public Optional<String> motivoRechazo() {
    return Optional.ofNullable(motivoRechazo);
  }

  public Instant vistoEn() {
    return vistoEn;
  }

  public Instant creadoEn() {
    return creadoEn;
  }
}
