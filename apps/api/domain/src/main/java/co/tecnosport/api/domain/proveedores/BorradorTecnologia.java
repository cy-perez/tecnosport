// Sin exigir productoId en APROBADO: nace con él, pero el producto se puede borrar del catálogo
// después y la constancia tiene que poder releerse sin él. Mismo criterio que BorradorProducto.
package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.Producto;
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
 *
 * <h2>Las fotos llegan con el borrador</h2>
 *
 * <p>Desde ADR-0079 la skill de fichas sube al borrador las fotos ya retocadas del modelo, en el
 * orden en que se publicarán: la primera es la principal. Quien revisa ve el producto antes de
 * decidir colores y precio, y aprobar las pasa al producto. Desde el 23/10/2026 quien revisa
 * también puede subir, quitar y reordenar fotos desde el panel: lo que entra por ahí no pasó por la
 * revisión de calidad del bloque 2, y lo decide esa persona.
 *
 * <h2>La ficha que preparó la skill se puede editar</h2>
 *
 * <p>Quien revisa corrige o recorta la descripción antes de aprobar ({@link
 * #editarDescripcion(String)}), y aprobar publica la editada ({@link #descripcion()}). La edición
 * se guarda aparte de la de la lista: una lista nueva actualiza la de la skill y <b>no pisa la
 * edición</b> (decisión del negocio del 23/10/2026); solo deja dicho que trajo otra ficha ({@link
 * #fichaNuevaDeLaSkill()}), para que quien revisa decida si vuelve a ella.
 */
public final class BorradorTecnologia {

  /** La principal y la galería entera del producto: más no cabe al aprobar. */
  public static final int TOPE_DE_FOTOS = 1 + Producto.TOPE_DE_GALERIA;

  /**
   * Las que necesita un modelo nuevo para aprobarse (decisión del negocio del 10/10/2026,
   * ADR-0079). La skill de fotos ya no deja pasar un modelo con menos; esto es para que la
   * compuerta no dependa solo de ella. Un modelo sin más tomas oficiales puede traer una {@link
   * ExcepcionDeFotos} que lo baja (28/10/2026).
   */
  public static final int MINIMO_DE_FOTOS = 4;

  private final UUID id;
  private final UUID proveedorId;
  private ModeloDeLista modelo;
  private final HuellaProveedor huella;
  private final List<ConfiguracionTecnologia> configuraciones;
  private final List<FotoDeBorradorTecnologia> fotos;
  private UUID productoId;
  private EstadoBorrador estado;
  private String motivoRechazo;
  private Instant vistoEn;
  private final Instant creadoEn;
  private String descripcionEditada;
  private boolean fichaNuevaDeLaSkill;
  private ExcepcionDeFotos excepcionDeFotos;

  public BorradorTecnologia(
      UUID id,
      UUID proveedorId,
      ModeloDeLista modelo,
      List<ConfiguracionTecnologia> configuraciones,
      UUID productoId,
      EstadoBorrador estado,
      String motivoRechazo,
      Instant vistoEn,
      Instant creadoEn,
      List<FotoDeBorradorTecnologia> fotos,
      String descripcionEditada,
      boolean fichaNuevaDeLaSkill,
      ExcepcionDeFotos excepcionDeFotos) {
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
    this.fotos = new ArrayList<>(fotos == null ? List.of() : fotos);
    this.descripcionEditada = enBlancoEsNulo(descripcionEditada);
    this.fichaNuevaDeLaSkill = this.descripcionEditada != null && fichaNuevaDeLaSkill;
    this.excepcionDeFotos = excepcionDeFotos;
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
        ahora,
        List.of(),
        null,
        false,
        null);
  }

  /**
   * Llegó otra lista con este modelo antes de que nadie lo revisara: manda la de hoy —costos,
   * configuraciones, colores sugeridos, descripción—, y lo que ya se había elegido sobre una
   * configuración que sigue en la lista se conserva. Lo elegido sobre una que ya no viene se pierde
   * con ella. Una lista más vieja que la última vista no cambia nada.
   *
   * <p>La ficha editada a mano tampoco se pierde: la de la skill se actualiza debajo, y si cambió,
   * {@link #fichaNuevaDeLaSkill()} lo avisa.
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
      nuevas.add(anterior == null ? hoy : hoy.conLaEleccionDe(anterior, deHoy.paleta()));
    }
    if (descripcionEditada != null && !deHoy.descripcion().equals(modelo.descripcion())) {
      this.fichaNuevaDeLaSkill = true;
    }
    this.modelo = deHoy;
    this.configuraciones.clear();
    this.configuraciones.addAll(nuevas);
    this.vistoEn = visto;
  }

  /**
   * Suma una foto al final: la primera que entra es la principal. Una foto con el mismo contenido
   * que otra no entra, porque publicaría dos veces la misma toma.
   */
  public void agregarFoto(FotoDeBorradorTecnologia foto) {
    exigirEnRevision("agregarle fotos a");
    Objects.requireNonNull(foto, "La foto no puede ser nula.");
    if (fotos.size() >= TOPE_DE_FOTOS) {
      throw new ExcepcionDeDominio(
          "El borrador ya tiene "
              + TOPE_DE_FOTOS
              + " fotos: la principal y la galería entera del producto.");
    }
    if (fotos.stream().anyMatch(f -> f.hash().equals(foto.hash()))) {
      throw new ExcepcionDeDominio("Esa foto ya está en el borrador.");
    }
    fotos.add(foto);
  }

  /**
   * Fija la excepción de fotos que declaró la skill para este modelo, o la quita con nulo. La manda
   * la importación de borradores en cada corrida, así que la de hoy reemplaza a la anterior: si la
   * persona la deshizo en la skill, el borrador vuelve a pedir {@link #MINIMO_DE_FOTOS}.
   */
  public void declararExcepcionDeFotos(ExcepcionDeFotos excepcion) {
    exigirEnRevision("cambiar la excepción de fotos de");
    this.excepcionDeFotos = excepcion;
  }

  /** Quita la foto y la devuelve, para que quien llama borre sus archivos. */
  public FotoDeBorradorTecnologia quitarFoto(UUID fotoId) {
    exigirEnRevision("quitarle fotos a");
    FotoDeBorradorTecnologia foto =
        fotos.stream()
            .filter(f -> f.id().equals(fotoId))
            .findFirst()
            .orElseThrow(() -> new ExcepcionDeDominio("Esa foto no es de este borrador."));
    fotos.remove(foto);
    return foto;
  }

  /**
   * Deja las fotos en el orden de {@code ids}: la primera pasa a ser la principal. Tienen que ser
   * exactamente las del borrador —ni una de más, ni una de menos, ni repetidas—: un orden a medias
   * dejaría fuera una foto sin que nadie lo pidiera.
   */
  public void reordenarFotos(List<UUID> ids) {
    exigirEnRevision("reordenar las fotos de");
    Objects.requireNonNull(ids, "El orden de las fotos no puede ser nulo.");
    Map<UUID, FotoDeBorradorTecnologia> porId = new LinkedHashMap<>();
    for (FotoDeBorradorTecnologia foto : fotos) {
      porId.put(foto.id(), foto);
    }
    if (ids.size() != porId.size() || !porId.keySet().equals(new LinkedHashSet<>(ids))) {
      throw new ExcepcionDeDominio(
          "El orden tiene que nombrar cada foto del borrador una sola vez.");
    }
    List<FotoDeBorradorTecnologia> ordenadas = ids.stream().map(porId::get).toList();
    fotos.clear();
    fotos.addAll(ordenadas);
  }

  /**
   * La ficha que se publicará en vez de la de la skill. En blanco o nula vuelve a la de la skill.
   * No admite marcadores {@code [[ ]]}: la ficha los pintaría tal cual (ADR-0025).
   */
  public void editarDescripcion(String texto) {
    exigirEnRevision("editar la ficha de");
    String limpio = enBlancoEsNulo(texto);
    if (limpio != null && limpio.contains("[[")) {
      throw new ExcepcionDeDominio(
          "La ficha tiene un marcador [[ ]]: se publicaría tal cual. Quítalo antes de guardar.");
    }
    this.descripcionEditada = limpio;
    this.fichaNuevaDeLaSkill = false;
  }

  /**
   * Lo que quien revisa decide de una configuración.
   *
   * @param precioVenta el <b>precio de contado</b> (ADR-0095); el publicado lo calcula el catálogo
   *     al aprobar, con la comisión de la pasarela
   */
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
          if (modelo.paleta().stream()
              .noneMatch(p -> ConfiguracionTecnologia.mismoColor(p, color))) {
            throw new ExcepcionDeDominio(
                "El color '" + color + "' no está en la paleta de " + modelo.titulo() + ".");
          }
        }
      }
      if (eleccion.precioVenta() != null && eleccion.precioVenta().valor().signum() == 0) {
        throw new ExcepcionDeDominio("El precio de contado no puede ser cero.");
      }
      actuales.put(actual.sku(), actual.elegir(colores, eleccion.precioVenta()));
    }
    this.configuraciones.replaceAll(c -> actuales.get(c.sku()));
  }

  /**
   * @param productoId el producto que nació, o el que ya existía y recibió las variantes
   * @param margenMinimo el de tecnología, con el tope de ganancia configurado
   */
  public void aprobar(UUID productoId, MargenMinimo margenMinimo) {
    exigirEnRevision("aprobar");
    exigirAprobable(margenMinimo);
    this.productoId =
        Objects.requireNonNull(productoId, "Aprobar es crear o completar un producto.");
    this.estado = EstadoBorrador.APROBADO;
  }

  /**
   * Lo que impide aprobar, dicho antes de tocar el catálogo: un modelo nuevo sin sus fotos, ninguna
   * configuración con colores, o una con colores y sin precio. Un borrador de configuraciones
   * nuevas no pide fotos: el producto ya tiene las suyas.
   */
  public void exigirAprobable(MargenMinimo margenMinimo) {
    Objects.requireNonNull(margenMinimo, "Aprobar tecnología exige su margen mínimo.");
    if (fotosQueFaltan() > 0) {
      throw new ExcepcionDeDominio(
          "Un modelo nuevo necesita "
              + minimoDeFotos()
              + " fotos para aprobarse; "
              + modelo.titulo()
              + " tiene "
              + fotos.size()
              + ".");
    }
    List<ConfiguracionTecnologia> vendibles = configuracionesQueSeVenden();
    if (vendibles.isEmpty()) {
      throw new ExcepcionDeDominio(
          "Elige los colores de al menos una configuración: sin eso no hay nada que vender.");
    }
    for (ConfiguracionTecnologia c : vendibles) {
      if (c.precioVenta() == null) {
        throw new ExcepcionDeDominio("Falta el precio de contado de " + c.titulo() + ".");
      }
      // Vender al costo no es vender (tools/cargar-catalogo.mjs): un cero de menos al teclear se
      // publicaría y se vendería con pérdida en cada unidad. Y superarlo por poco tampoco: hace
      // falta el margen mínimo de tecnología: el 10 % de la regla 18 con el tope de ganancia (27 de
      // octubre de 2026). Se compara el de contado, que es lo que el negocio recibe: el publicado
      // lleva encima la comisión, que no es ganancia.
      margenMinimo.exigir(c.precioVenta(), c.costoProveedor(), c.titulo());
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

  /** La ficha que se publica al aprobar: la editada a mano si la hay, la de la skill si no. */
  public String descripcion() {
    return descripcionEditada != null ? descripcionEditada : modelo.descripcion();
  }

  /** La edición a mano; vacía mientras se publique la de la skill. */
  public Optional<String> descripcionEditada() {
    return Optional.ofNullable(descripcionEditada);
  }

  /** Si una lista trajo otra ficha de la skill después de la edición a mano. */
  public boolean fichaNuevaDeLaSkill() {
    return fichaNuevaDeLaSkill;
  }

  public HuellaProveedor huella() {
    return huella;
  }

  public List<ConfiguracionTecnologia> configuraciones() {
    return List.copyOf(configuraciones);
  }

  /**
   * Cuántas fotos le faltan para aprobarse. Un borrador de configuraciones nuevas no pide: el
   * producto ya tiene las suyas. Lo dice el dominio para que el panel no tenga que saber el mínimo.
   */
  public int fotosQueFaltan() {
    return productoId != null ? 0 : Math.max(0, minimoDeFotos() - fotos.size());
  }

  /** Las fotos que pide este modelo: {@link #MINIMO_DE_FOTOS}, o las de su excepción. */
  public int minimoDeFotos() {
    return excepcionDeFotos != null ? excepcionDeFotos.minimo() : MINIMO_DE_FOTOS;
  }

  public Optional<ExcepcionDeFotos> excepcionDeFotos() {
    return Optional.ofNullable(excepcionDeFotos);
  }

  /** En el orden en que se publican: la primera es la principal. */
  public List<FotoDeBorradorTecnologia> fotos() {
    return List.copyOf(fotos);
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
