// Sin exigir productoId en APROBADO ni en RENOVACION_APLICADA: nacen con él, pero el producto
// se puede borrar del catálogo después y la constancia tiene que poder releerse sin él.
package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que el extractor sacó de una publicación, esperando a que una persona lo apruebe.
 *
 * <p>Guarda el JSON completo que devolvió el extractor además de los campos normalizados: los
 * campos son lo que el panel edita, y el JSON es lo que permite ver qué dijo el modelo antes de que
 * nadie lo tocara. Cuando el prompt cambie, es lo que deja comparar.
 *
 * <h2>Un borrador con alertas nunca se publica solo</h2>
 *
 * <p>{@link #esPublicableAutomaticamente()} es falso con cualquier alerta, y hoy es falso siempre
 * porque la publicación automática no está encendida para ningún proveedor. Existe para que el día
 * que se encienda, la regla ya esté escrita aquí y no en el caso de uso.
 */
public final class BorradorProducto {

  private final UUID id;
  private final UUID publicacionId;
  private final UUID proveedorId;
  private final String extraccionCruda;
  private String titulo;
  private LineaCatalogo linea;
  private TipoProductoProveedor tipo;
  private Dinero precioProveedor;

  /**
   * El <b>precio de contado</b> sugerido (ADR-0095): lo que calcula {@link CalculadoraDeMargen}, o
   * lo que quien revisa escribió encima. El publicado no se guarda: sale de este con la comisión de
   * la pasarela cuando se muestra, y al aprobar lo calcula el catálogo.
   */
  private Dinero precioVentaSugerido;

  private Tallas tallas;
  private Integer cantidadTonos;
  private List<String> tonosNombrados;
  private String material;
  private String descripcion;
  private String altEn;
  private final HuellaProveedor huella;
  private PHash pHash;
  private final Set<AlertaBorrador> alertas;
  private final Set<UUID> fotosDescartadas;
  private final List<FotoSubida> fotosSubidas;
  private TallasPorTono tallasPorTono;
  private final List<PrecioAdicional> preciosAdicionales;
  private final Map<UUID, String> tonosSugeridos;
  private final String lecturaDeFotos;
  private final List<UUID> fotosAgregadas;
  private EstadoBorrador estado;
  private UUID productoId;
  private String motivoRechazo;
  private final Instant creadoEn;

  /**
   * La etiqueta de diseño que el lector le dio a sus fotos en un álbum; nula si no hay. Es lo que
   * deja al panel decir «posible mismo diseño» de dos borradores de la misma publicación: un diseño
   * en seis colores con un SKU por color son seis borradores (decidido el 10 de octubre de 2026), y
   * quien revisa los une si son el mismo producto ({@link #absorber}).
   */
  private String diseno;

  /**
   * Con qué prenda se armó el set y de dónde salió su costo; nulo si el borrador es la prenda sola
   * ({@link ArmadorDeSets}, 12 de octubre de 2026). Lo pone la ingesta y lo devuelve el adaptador,
   * como el diseño.
   */
  private ArmadoDeSet set;

  /**
   * Cuántas tallas más arriba nombra el proveedor la que se vende (ADR-0085); {@link
   * AjusteDeTalla#NINGUNO} si las nombra igual. Con él, aprobar deja en cada variante qué talla
   * pedirle al proveedor. Lo pone la ingesta y lo devuelve el adaptador, como el set.
   */
  private AjusteDeTalla ajusteDeTalla = AjusteDeTalla.NINGUNO;

  /**
   * Para quién es el tenis, según la foto o el anuncio (ADR-0089): decide la rama del calzado que
   * el panel propone al aprobar. Nulo si no se sabe. Lo pone la ingesta y lo devuelve el adaptador,
   * como el ajuste de talla.
   *
   * <p>Desde el 29 de octubre de 2026 también el de una prenda de ropa, y por eso el nombre se le
   * quedó corto: la partición dama/caballero ya lo usaba para la ropa (ADR-0085), y con él el panel
   * propone Ropa › Dama o Ropa › Caballero. Renombrarlo movía la columna y el contrato.
   */
  private GeneroDeCalzado generoDeCalzado;

  public BorradorProducto(
      UUID id,
      UUID publicacionId,
      UUID proveedorId,
      String extraccionCruda,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Dinero precioVentaSugerido,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Set<UUID> fotosDescartadas,
      List<FotoSubida> fotosSubidas,
      EstadoBorrador estado,
      UUID productoId,
      String motivoRechazo,
      Instant creadoEn) {
    this(
        id,
        publicacionId,
        proveedorId,
        extraccionCruda,
        titulo,
        linea,
        tipo,
        precioProveedor,
        precioVentaSugerido,
        tallas,
        cantidadTonos,
        tonosNombrados,
        material,
        descripcion,
        altEn,
        huella,
        pHash,
        alertas,
        fotosDescartadas,
        fotosSubidas,
        null,
        null,
        null,
        null,
        null,
        estado,
        productoId,
        motivoRechazo,
        creadoEn);
  }

  /**
   * @param tallasPorTono las tallas de cada tono, cuando el mensaje no las tiene todas en todos
   * @param preciosAdicionales los del anuncio que no son de este producto: acompañantes, combos y
   *     promociones
   * @param tonosSugeridos el color que la lectura de fotos vio en cada foto, por mensaje; solo las
   *     fotos de un solo color. Lo que el panel propone al aprobar, no lo que se aprueba
   * @param lecturaDeFotos el JSON que devolvió la lectura de fotos, tal cual; nulo si no se leyeron
   * @param fotosAgregadas fotos de <b>otras</b> publicaciones del proveedor que se sumaron a este
   *     borrador, por mensaje y en orden: la misma referencia publicada otra vez, o una foto que
   *     quien revisa movió aquí desde otro borrador
   */
  public BorradorProducto(
      UUID id,
      UUID publicacionId,
      UUID proveedorId,
      String extraccionCruda,
      String titulo,
      LineaCatalogo linea,
      TipoProductoProveedor tipo,
      Dinero precioProveedor,
      Dinero precioVentaSugerido,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Set<UUID> fotosDescartadas,
      List<FotoSubida> fotosSubidas,
      TallasPorTono tallasPorTono,
      List<PrecioAdicional> preciosAdicionales,
      Map<UUID, String> tonosSugeridos,
      String lecturaDeFotos,
      List<UUID> fotosAgregadas,
      EstadoBorrador estado,
      UUID productoId,
      String motivoRechazo,
      Instant creadoEn) {
    this.id = Objects.requireNonNull(id, "El id del borrador no puede ser nulo.");
    this.publicacionId =
        Objects.requireNonNull(publicacionId, "Un borrador sale de una publicación.");
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un borrador es de un proveedor.");
    this.extraccionCruda =
        Objects.requireNonNull(extraccionCruda, "El borrador guarda lo que devolvió el extractor.");
    this.titulo = enBlancoEsNulo(titulo);
    this.linea = linea;
    this.tipo = tipo == null ? TipoProductoProveedor.OTRO : tipo;
    this.precioProveedor = precioProveedor;
    this.precioVentaSugerido = precioVentaSugerido;
    this.tallas = tallas == null ? Tallas.desconocida() : tallas;
    this.cantidadTonos = cantidadTonos;
    this.tonosNombrados = tonosNombrados == null ? List.of() : List.copyOf(tonosNombrados);
    this.material = enBlancoEsNulo(material);
    this.descripcion = enBlancoEsNulo(descripcion);
    this.altEn = enBlancoEsNulo(altEn);
    this.huella = huella;
    this.pHash = pHash;
    // EnumSet.copyOf revienta con una colección vacía que no sea EnumSet; se copia a mano.
    this.alertas = EnumSet.noneOf(AlertaBorrador.class);
    if (alertas != null) {
      this.alertas.addAll(alertas);
    }
    this.fotosDescartadas = new LinkedHashSet<>();
    if (fotosDescartadas != null) {
      this.fotosDescartadas.addAll(fotosDescartadas);
    }
    this.fotosSubidas = new ArrayList<>();
    if (fotosSubidas != null) {
      this.fotosSubidas.addAll(fotosSubidas);
    }
    this.tallasPorTono = tallasPorTono == null ? TallasPorTono.ninguna() : tallasPorTono;
    this.preciosAdicionales =
        preciosAdicionales == null ? List.of() : List.copyOf(preciosAdicionales);
    this.tonosSugeridos = new LinkedHashMap<>();
    if (tonosSugeridos != null) {
      this.tonosSugeridos.putAll(tonosSugeridos);
    }
    this.lecturaDeFotos = enBlancoEsNulo(lecturaDeFotos);
    this.fotosAgregadas = new ArrayList<>();
    if (fotosAgregadas != null) {
      fotosAgregadas.stream().distinct().forEach(this.fotosAgregadas::add);
    }
    this.estado = Objects.requireNonNull(estado, "El estado del borrador no puede ser nulo.");
    this.productoId = productoId;
    this.motivoRechazo = enBlancoEsNulo(motivoRechazo);
    this.creadoEn = Objects.requireNonNull(creadoEn, "El borrador tiene fecha.");

    if (estado == EstadoBorrador.RECHAZADO && this.motivoRechazo == null) {
      throw new ExcepcionDeDominio("Un borrador rechazado dice por qué.");
    }
    if (cantidadTonos != null && cantidadTonos < 0) {
      throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
    }
  }

  /** Un producto nuevo, a la espera de revisión. */
  public static BorradorProducto nuevo(
      UUID publicacionId,
      UUID proveedorId,
      ProductoExtraido extraido,
      String extraccionCruda,
      Dinero precioProveedor,
      Dinero precioVentaSugerido,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Instant ahora) {
    return nuevo(
        publicacionId,
        proveedorId,
        extraido,
        extraccionCruda,
        precioProveedor,
        precioVentaSugerido,
        huella,
        pHash,
        alertas,
        FotosDelProducto.todas(),
        ahora);
  }

  /**
   * Un producto nuevo con sus fotos ya repartidas: las de la publicación que no son suyas nacen
   * descartadas —se pueden recuperar partiendo otro borrador, no se borran— y las que la lectura de
   * fotos vio de un solo color llegan con ese tono sugerido.
   */
  public static BorradorProducto nuevo(
      UUID publicacionId,
      UUID proveedorId,
      ProductoExtraido extraido,
      String extraccionCruda,
      Dinero precioProveedor,
      Dinero precioVentaSugerido,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      FotosDelProducto fotos,
      Instant ahora) {
    Objects.requireNonNull(extraido, "El borrador nace de una extracción.");
    Objects.requireNonNull(fotos, "El borrador sabe cuáles fotos son suyas.");
    BorradorProducto borrador =
        new BorradorProducto(
            GeneradorIdentificador.nuevo(),
            publicacionId,
            proveedorId,
            extraccionCruda,
            extraido.titulo(),
            extraido.linea(),
            extraido.tipo(),
            precioProveedor,
            precioVentaSugerido,
            extraido.tallas(),
            extraido.cantidadTonos(),
            extraido.tonosNombrados(),
            extraido.material(),
            extraido.descripcion(),
            extraido.altEn(),
            huella,
            pHash,
            alertas,
            fotos.ajenas(),
            List.of(),
            extraido.tallasPorTono(),
            extraido.preciosAdicionales(),
            fotos.tonosSugeridos(),
            fotos.lecturaCruda(),
            List.of(),
            EstadoBorrador.EN_REVISION,
            null,
            null,
            ahora);
    borrador.anotarDiseno(fotos.diseno());
    return borrador;
  }

  /** Lo que cabe en la columna: el modelo no tiene tope para la etiqueta. */
  static final int LARGO_MAXIMO_DEL_DISENO = 120;

  /**
   * La etiqueta de diseño del lector. Solo la ponen la creación del borrador y el adaptador, para
   * devolverlo como estaba guardado: no es algo que un caso de uso decida.
   */
  void anotarDiseno(String diseno) {
    this.diseno = etiquetaDeDiseno(diseno);
  }

  /** Para el adaptador, que vive en otro paquete. */
  public static BorradorProducto conDiseno(BorradorProducto borrador, String diseno) {
    borrador.anotarDiseno(diseno);
    return borrador;
  }

  static String etiquetaDeDiseno(String diseno) {
    if (diseno == null || diseno.isBlank()) {
      return null;
    }
    String limpia = diseno.strip();
    return limpia.length() <= LARGO_MAXIMO_DEL_DISENO
        ? limpia
        : limpia.substring(0, LARGO_MAXIMO_DEL_DISENO).strip();
  }

  public Optional<String> diseno() {
    return Optional.ofNullable(diseno);
  }

  /** Para la ingesta y el adaptador: el borrador es un set, armado así. */
  public static BorradorProducto conSet(BorradorProducto borrador, ArmadoDeSet set) {
    borrador.set = set;
    return borrador;
  }

  public Optional<ArmadoDeSet> set() {
    return Optional.ofNullable(set);
  }

  /** Para la ingesta y el adaptador: el proveedor nombra las tallas así. */
  public static BorradorProducto conAjusteDeTalla(BorradorProducto borrador, AjusteDeTalla ajuste) {
    borrador.ajusteDeTalla = Objects.requireNonNull(ajuste, "El ajuste de talla no es nulo.");
    return borrador;
  }

  public AjusteDeTalla ajusteDeTalla() {
    return ajusteDeTalla;
  }

  /** Para la ingesta y el adaptador: para quién es el tenis o la prenda. */
  public static BorradorProducto conGeneroDeCalzado(
      BorradorProducto borrador, GeneroDeCalzado genero) {
    borrador.generoDeCalzado = genero;
    return borrador;
  }

  public Optional<GeneroDeCalzado> generoDeCalzado() {
    return Optional.ofNullable(generoDeCalzado);
  }

  /**
   * Para la ingesta: el anuncio nombra las tallas corridas, y el borrador pasa a las que se venden
   * —«Tallas M-L-XL-XXL» con horma reducida son S, M, L, XL— y guarda el ajuste para decir cuál
   * pedirle al proveedor (ADR-0085). Las de cada tono también. Todas o ninguna: si una no se puede
   * correr, se quedan las del anuncio con {@link AlertaBorrador#TALLAS_SIN_CORRER}.
   */
  public static BorradorProducto conTallasCorridas(
      BorradorProducto borrador, AjusteDeTalla ajuste) {
    Objects.requireNonNull(ajuste, "El ajuste de talla no es nulo.");
    borrador.ajusteDeTalla = ajuste;
    if (ajuste.ninguno()) {
      return borrador;
    }
    int paso = AjusteDeTalla.pasoDe(borrador.tipo);
    if (borrador.tallas.tipo() == TipoDeTalla.UNICA) {
      // Una talla única se pide como se vende: no hay otra que pedirle al proveedor.
      borrador.ajusteDeTalla = AjusteDeTalla.NINGUNO;
      return borrador;
    }
    if (borrador.tallas.tipo() == TipoDeTalla.DESCONOCIDA) {
      // El ajuste queda, y quien escriba las tallas tiene que escribir las que se venden.
      borrador.alertas.add(AlertaBorrador.TALLAS_SIN_CORRER);
      return borrador;
    }
    Optional<List<String>> tallas = corridas(borrador.tallas.valores(), ajuste, paso);
    List<TallasPorTono.TallasDeUnTono> porTono = new ArrayList<>();
    boolean todasLasDeLosTonos = true;
    for (TallasPorTono.TallasDeUnTono tono : borrador.tallasPorTono.tonos()) {
      Optional<List<String>> suyas = corridas(tono.tallas(), ajuste, paso);
      todasLasDeLosTonos &= suyas.isPresent();
      suyas.ifPresent(t -> porTono.add(new TallasPorTono.TallasDeUnTono(tono.tono(), t)));
    }
    if (tallas.isEmpty() || !todasLasDeLosTonos) {
      borrador.alertas.add(AlertaBorrador.TALLAS_SIN_CORRER);
      return borrador;
    }
    borrador.tallas = Tallas.lista(tallas.get());
    borrador.tallasPorTono = new TallasPorTono(porTono);
    borrador.alertas.add(AlertaBorrador.TALLAS_CORRIDAS);
    return borrador;
  }

  /**
   * El borrador de caballero de un anuncio unisex que nombra distinto las tallas de cada género:
   * «Horma normal Dama, Aumenta 1 Cab» (Imperio Wicho, 10 de octubre de 2026; ADR-0085). Las mismas
   * fotos, el mismo texto y el mismo costo; otro título, sus tallas corridas con su ajuste, y una
   * huella derivada de la de este, para que aprobar los dos no choque. Nace sin huella visual, como
   * una parte ({@link #partir}): la toma de su principal al aprobarse.
   *
   * @param titulo el suyo, con su género
   */
  public BorradorProducto delOtroGenero(String titulo, AjusteDeTalla ajuste, Instant ahora) {
    exigirEnRevision("partir por género");
    Objects.requireNonNull(ajuste, "El ajuste de talla no es nulo.");
    Set<AlertaBorrador> alertasDelNuevo = EnumSet.noneOf(AlertaBorrador.class);
    alertasDelNuevo.addAll(alertas);
    BorradorProducto otro =
        new BorradorProducto(
            GeneradorIdentificador.nuevo(),
            publicacionId,
            proveedorId,
            extraccionCruda,
            titulo,
            linea,
            tipo,
            precioProveedor,
            precioVentaSugerido,
            tallas,
            cantidadTonos,
            tonosNombrados,
            material,
            descripcion,
            altEn,
            huella == null ? null : HuellaProveedor.delGemelo(huella),
            null,
            alertasDelNuevo,
            fotosDescartadas,
            List.of(),
            tallasPorTono,
            preciosAdicionales,
            tonosSugeridos,
            lecturaDeFotos,
            List.of(),
            EstadoBorrador.EN_REVISION,
            null,
            null,
            ahora);
    otro.set = set;
    return conTallasCorridas(otro, ajuste);
  }

  /**
   * ¿Es el otro género del mismo anuncio unisex ({@link #delOtroGenero})? Los dos conservan las
   * mismas fotos, y una foto de los dos no es «de otro producto».
   */
  public boolean esGemeloDe(BorradorProducto otro) {
    return publicacionId.equals(otro.publicacionId)
        && huella != null
        && otro.huella != null
        && (otro.huella.equals(HuellaProveedor.delGemelo(huella))
            || huella.equals(HuellaProveedor.delGemelo(otro.huella)));
  }

  /** Para la ingesta: el título de la parte de este género de un anuncio unisex. */
  public void titularPorGenero(String titulo) {
    exigirEnRevision("titular");
    this.titulo = enBlancoEsNulo(titulo);
  }

  /**
   * Quien revisa corrige cómo nombra el proveedor las tallas. No vuelve a correrlas: las del
   * borrador son siempre las que se venden.
   */
  public void cambiarAjusteDeTalla(AjusteDeTalla ajuste) {
    exigirEnRevision("cambiar el ajuste de talla de");
    this.ajusteDeTalla = Objects.requireNonNull(ajuste, "El ajuste de talla no es nulo.");
  }

  /**
   * Cómo le pide al proveedor cada talla del borrador, con el paso de su tipo de prenda: lo que el
   * panel enseña al lado de cada una. Vacía sin ajuste o sin lista de tallas.
   */
  public List<TallaPedida> tallasPedidas() {
    if (ajusteDeTalla.ninguno() || tallas.tipo() != TipoDeTalla.LISTA) {
      return List.of();
    }
    int paso = AjusteDeTalla.pasoDe(tipo);
    return tallas.valores().stream()
        .map(t -> new TallaPedida(t, ajusteDeTalla.tallaDelProveedorConPaso(t, paso).orElse(null)))
        .toList();
  }

  /**
   * Las que se venden de las que el proveedor imprimió en una foto, con el ajuste de este borrador:
   * en el de caballero de un anuncio unisex, la 42 de la foto es la 41 (ADR-0085). Las que no se
   * pueden correr no se proponen.
   */
  public List<String> queSeVendenDe(List<String> impresas) {
    int paso = AjusteDeTalla.pasoDe(tipo);
    return impresas.stream()
        .map(t -> ajusteDeTalla.tallaQueSeVende(t, paso))
        .flatMap(Optional::stream)
        .toList();
  }

  /**
   * @param talla la que se vende
   * @param delProveedor la que se le pide al proveedor; nula si no se puede contar
   */
  public record TallaPedida(String talla, String delProveedor) {}

  private static Optional<List<String>> corridas(
      List<String> delAnuncio, AjusteDeTalla ajuste, int paso) {
    List<String> vendidas = new ArrayList<>();
    for (String talla : delAnuncio) {
      Optional<String> vendida = ajuste.tallaQueSeVende(talla, paso);
      if (vendida.isEmpty()) {
        return Optional.empty();
      }
      vendidas.add(vendida.get());
    }
    return Optional.of(vendidas);
  }

  /** ¿La lectura de fotos vio el mismo diseño en este y en el otro? */
  public boolean mismoDisenoQue(BorradorProducto otro) {
    return diseno != null
        && otro.diseno != null
        && publicacionId.equals(otro.publicacionId)
        && diseno.equalsIgnoreCase(otro.diseno);
  }

  /**
   * Se queda con otro borrador de la misma publicación: el mismo diseño en otro color, que el álbum
   * dio aparte por traer otro SKU (camino acordado el 11 de octubre de 2026). Recupera las fotos
   * que el otro conservaba, con sus tonos sugeridos, y las que se le sumaron de otras
   * publicaciones; junta los tonos de los dos, y si las tallas no son las mismas, guarda las de
   * cada tono para no publicar una talla que un color no tiene. El otro se elimina después: su SKU
   * deja de reconocer renovaciones, y ese es el costo de unirlos.
   *
   * <p>Las fotos subidas desde el panel no se pasan: son archivos del otro borrador, y se irían con
   * él. Con alguna, no se une.
   *
   * @param fotosDeLaPublicacion las de la publicación, en orden
   */
  public void absorber(BorradorProducto otro, List<UUID> fotosDeLaPublicacion) {
    exigirEnRevision("unir");
    Objects.requireNonNull(otro, "Unir dice con cuál.");
    if (otro.id.equals(id)) {
      throw new ExcepcionDeDominio("Un borrador no se une consigo mismo.");
    }
    if (otro.estado != EstadoBorrador.EN_REVISION) {
      throw new ExcepcionDeDominio("Solo se une un borrador que sigue en revisión.");
    }
    if (!otro.publicacionId.equals(publicacionId)) {
      throw new ExcepcionDeDominio("Solo se unen borradores de la misma publicación.");
    }
    if (!otro.fotosSubidas.isEmpty()) {
      throw new ExcepcionDeDominio(
          "El otro borrador tiene fotos subidas desde el panel: súbelas en este antes de unirlos.");
    }
    if (!otro.ajusteDeTalla.equals(ajusteDeTalla)) {
      throw new ExcepcionDeDominio(
          "El proveedor nombra las tallas de los dos de distinta manera: no se sabría qué talla"
              + " pedirle por cada una.");
    }
    if (!tallasDeCadaTonoSeSaben(otro)) {
      throw new ExcepcionDeDominio(
          "Las tallas no son las mismas y uno de los dos no dice sus tonos: no se sabría qué talla"
              + " tiene cada color.");
    }
    List<String> tonosDeEste = tonosConocidos();
    List<String> tonosDelOtro = otro.tonosConocidos();
    boolean porTono =
        !tallas.equals(otro.tallas)
            || !tallasPorTono.estaVacia()
            || !otro.tallasPorTono.estaVacia();
    if (porTono && !tonosDeEste.isEmpty() && !tonosDelOtro.isEmpty()) {
      Map<String, List<String>> tallasDe = new LinkedHashMap<>();
      tallasPorTonoDe(tonosDeEste).forEach((t, v) -> juntar(tallasDe, t, v));
      otro.tallasPorTonoDe(tonosDelOtro).forEach((t, v) -> juntar(tallasDe, t, v));
      List<TallasPorTono.TallasDeUnTono> lista = new ArrayList<>();
      tallasDe.forEach((t, v) -> lista.add(new TallasPorTono.TallasDeUnTono(t, v)));
      this.tallasPorTono = new TallasPorTono(lista);
    }
    for (UUID foto : fotosDeLaPublicacion) {
      if (!otro.fotosDescartadas.contains(foto) && fotosDescartadas.remove(foto)) {
        String tono = otro.tonosSugeridos.get(foto);
        if (tono != null) {
          tonosSugeridos.put(foto, tono);
        }
      }
    }
    otro.fotosAgregadas.stream()
        .filter(f -> !fotosAgregadas.contains(f))
        .forEach(
            f -> {
              fotosAgregadas.add(f);
              String tono = otro.tonosSugeridos.get(f);
              if (tono != null) {
                tonosSugeridos.put(f, tono);
              }
            });
    List<String> unidos = new ArrayList<>(tonosDeEste);
    tonosDelOtro.stream().filter(t -> !unidos.contains(t)).forEach(unidos::add);
    if (!tallas.equals(otro.tallas)
        && tallas.tipo() == TipoDeTalla.LISTA
        && otro.tallas.tipo() == TipoDeTalla.LISTA) {
      List<String> todas = new ArrayList<>(tallas.valores());
      otro.tallas.valores().stream().filter(t -> !todas.contains(t)).forEach(todas::add);
      this.tallas = Tallas.lista(todas);
    }
    if (!unidos.isEmpty()) {
      this.tonosNombrados = List.copyOf(unidos);
      this.cantidadTonos = unidos.size();
    }
    if (otro.alertas.contains(AlertaBorrador.REPLICA)) {
      alertas.add(AlertaBorrador.REPLICA);
    }
    // Con las fotos del otro, este puede dejar de estar sin fotos.
    boolean sinNinguna =
        fotosDeLaPublicacion.stream().allMatch(fotosDescartadas::contains)
            && fotosAgregadas.isEmpty()
            && fotosSubidas.isEmpty();
    if (!sinNinguna) {
      alertas.remove(AlertaBorrador.SIN_FOTOS);
    }
  }

  /**
   * ¿Se puede saber qué tallas tiene cada color de los dos? Con las mismas tallas y sin tallas por
   * tono no hace falta; si no, los dos tienen que decir sus tonos: unir un crema en L y XL con uno
   * que no dice su color, en M, publicaría un crema M que no existe.
   */
  public boolean tallasDeCadaTonoSeSaben(BorradorProducto otro) {
    boolean hace =
        !tallas.equals(otro.tallas)
            || !tallasPorTono.estaVacia()
            || !otro.tallasPorTono.estaVacia();
    return !hace || (!tonosConocidos().isEmpty() && !otro.tonosConocidos().isEmpty());
  }

  /** Las tallas de cada uno de sus tonos: las de sus tallas por tono, o todas las suyas. */
  private Map<String, List<String>> tallasPorTonoDe(List<String> tonos) {
    Map<String, List<String>> porTono = new LinkedHashMap<>();
    for (String tono : tonos) {
      porTono.put(tono, tallasPorTono.tallasDe(tono).orElse(tallas.valores()));
    }
    return porTono;
  }

  private static void juntar(Map<String, List<String>> tallasDe, String tono, List<String> mas) {
    List<String> ya = new ArrayList<>(tallasDe.getOrDefault(tono, List.of()));
    mas.stream().filter(t -> !ya.contains(t)).forEach(ya::add);
    tallasDe.put(tono, List.copyOf(ya));
  }

  /** Los tonos que nombra, o si no nombra ninguno, los que sugirió la lectura de sus fotos. */
  private List<String> tonosConocidos() {
    if (!tonosNombrados.isEmpty()) {
      return tonosNombrados;
    }
    return tonosSugeridos.values().stream().distinct().toList();
  }

  /**
   * La constancia de una renovación: el mensaje reconoció un producto que ya existía. No pasa por
   * revisión, pero deja escrito qué dijo el proveedor y con qué alertas —{@code PRECIO_CAMBIO}, si
   * cambió el precio— para que el panel las enseñe.
   */
  public static BorradorProducto renovacionAplicada(
      UUID publicacionId,
      UUID proveedorId,
      UUID productoId,
      ProductoExtraido extraido,
      String extraccionCruda,
      Dinero precioProveedor,
      HuellaProveedor huella,
      PHash pHash,
      Set<AlertaBorrador> alertas,
      Instant ahora) {
    Objects.requireNonNull(extraido, "La renovación nace de una extracción.");
    return new BorradorProducto(
        GeneradorIdentificador.nuevo(),
        publicacionId,
        proveedorId,
        extraccionCruda,
        extraido.titulo(),
        extraido.linea(),
        extraido.tipo(),
        precioProveedor,
        null,
        extraido.tallas(),
        extraido.cantidadTonos(),
        extraido.tonosNombrados(),
        extraido.material(),
        extraido.descripcion(),
        extraido.altEn(),
        huella,
        pHash,
        alertas,
        Set.of(),
        List.of(),
        EstadoBorrador.RENOVACION_APLICADA,
        productoId,
        null,
        ahora);
  }

  /** Lo que el panel deja cambiar antes de aprobar. Lo que llegue nulo se queda como estaba. */
  public void editar(
      String titulo,
      TipoProductoProveedor tipo,
      Dinero precioVentaSugerido,
      Tallas tallas,
      Integer cantidadTonos,
      List<String> tonosNombrados,
      String material,
      String descripcion,
      String altEn) {
    exigirEnRevision("editar");
    // Quien corrige el modelo en el título, o las tallas, sin tocar la descripción del tenis: la
    // descripción los sigue (ADR-0092). Si la reescribió, manda lo que escribió.
    boolean descripcionIntacta =
        descripcion == null || Objects.equals(enBlancoEsNulo(descripcion), this.descripcion);
    Optional<String> recompuesta =
        descripcionIntacta
            ? descripcionDelTenisCon(
                titulo != null ? enBlancoEsNulo(titulo) : this.titulo,
                tallas != null ? tallas : this.tallas)
            : Optional.empty();
    if (titulo != null) {
      this.titulo = enBlancoEsNulo(titulo);
    }
    if (tipo != null) {
      this.tipo = tipo;
    }
    if (precioVentaSugerido != null) {
      this.precioVentaSugerido = precioVentaSugerido;
    }
    if (tallas != null) {
      this.tallas = tallas;
    }
    if (cantidadTonos != null) {
      if (cantidadTonos < 0) {
        throw new ExcepcionDeDominio("La cantidad de tonos no puede ser negativa.");
      }
      this.cantidadTonos = cantidadTonos;
    }
    if (tonosNombrados != null) {
      this.tonosNombrados = List.copyOf(tonosNombrados);
    }
    if (material != null) {
      this.material = enBlancoEsNulo(material);
    }
    if (descripcion != null) {
      this.descripcion = enBlancoEsNulo(descripcion);
    }
    if (altEn != null) {
      this.altEn = enBlancoEsNulo(altEn);
    }
    recompuesta.ifPresent(texto -> this.descripcion = texto);
  }

  /**
   * Para la ingesta, con el título, el género y las tallas que se venden ya resueltos: la
   * descripción de un tenis es la de {@link DescripcionDeTenis}, y lo que el extractor escribió
   * —cómo está hecho— va al final (ADR-0092). Se llama una vez: la segunda tomaría la descripción
   * compuesta por características.
   */
  public void describirComoTenis() {
    if (tipo != TipoProductoProveedor.TENIS) {
      return;
    }
    String compuesta =
        DescripcionDeTenis.componer(titulo, generoDeCalzado, tallasNumericas(tallas), descripcion);
    if (compuesta != null) {
      this.descripcion = compuesta;
    }
  }

  /**
   * La descripción de este tenis con otro título u otras tallas, si sigue siendo la que se compuso
   * con los de ahora: así sigue al modelo que corrige quien revisa (ADR-0092).
   *
   * @return vacía si no es un tenis, o si su descripción no es la compuesta —alguien la reescribió,
   *     o es de antes—
   */
  public Optional<String> descripcionDelTenisCon(String otroTitulo, Tallas otrasTallas) {
    if (tipo != TipoProductoProveedor.TENIS) {
      return Optional.empty();
    }
    return DescripcionDeTenis.caracteristicasDe(
            descripcion, titulo, generoDeCalzado, tallasNumericas(tallas))
        .map(
            caracteristicas ->
                DescripcionDeTenis.componer(
                    otroTitulo,
                    generoDeCalzado,
                    tallasNumericas(otrasTallas == null ? tallas : otrasTallas),
                    caracteristicas))
        .filter(Objects::nonNull);
  }

  private static List<String> tallasNumericas(Tallas tallas) {
    return tallas != null && tallas.tipo() == TipoDeTalla.LISTA ? tallas.valores() : List.of();
  }

  /**
   * Quien revisa saca una foto que no es de este producto. No se borra el archivo: la foto es de la
   * publicación, y otro borrador del mismo mensaje puede usarla; solo deja de ofrecerse aquí.
   */
  public void descartarFoto(UUID mensajeId) {
    exigirEnRevision("descartar fotos de");
    Objects.requireNonNull(mensajeId, "La foto no puede ser nula.");
    // Una de otra publicación no es de la publicación de este borrador: no hay nada que marcar
    // como descartado, sale de las agregadas y ya.
    if (!fotosAgregadas.remove(mensajeId)) {
      fotosDescartadas.add(mensajeId);
    }
    tonosSugeridos.remove(mensajeId);
  }

  /**
   * Suma fotos de otra publicación del mismo proveedor (10 de octubre de 2026): la misma referencia
   * que vuelve a salir —la camiseta 261002 de Violeta acompaña al jogger a las 10:35 y a la bermuda
   * a las 11:01— o una foto que quien revisa trae desde otro borrador. Las que ya tiene no se
   * repiten. Con alguna foto nueva deja de estar {@code SIN_FOTOS}: quien llama solo suma fotos con
   * archivo.
   *
   * @param fotosDeLaPublicacion las de su propia publicación, que no son «otra publicación»
   * @return las que de verdad se sumaron
   */
  public List<UUID> agregarFotos(List<UUID> fotos, List<UUID> fotosDeLaPublicacion) {
    exigirEnRevision("sumar fotos a");
    Objects.requireNonNull(fotos, "Las fotos no pueden ser nulas.");
    List<UUID> sumadas = new ArrayList<>();
    for (UUID foto : fotos) {
      if (foto != null
          && !fotosDeLaPublicacion.contains(foto)
          && !fotosAgregadas.contains(foto)
          && !sumadas.contains(foto)) {
        sumadas.add(foto);
      }
    }
    fotosAgregadas.addAll(sumadas);
    if (!sumadas.isEmpty()) {
      alertas.remove(AlertaBorrador.SIN_FOTOS);
    }
    return sumadas;
  }

  /**
   * Devuelve una foto de su propia publicación que estaba descartada: la que quien revisa mueve
   * aquí desde otro borrador del mismo mensaje.
   */
  public void recuperarFoto(UUID mensajeId) {
    exigirEnRevision("recuperar fotos de");
    if (!fotosDescartadas.remove(Objects.requireNonNull(mensajeId, "La foto no puede ser nula."))) {
      throw new ExcepcionDeDominio("Esa foto no estaba descartada en el borrador.");
    }
    alertas.remove(AlertaBorrador.SIN_FOTOS);
  }

  /**
   * Parte el borrador en dos: las fotos que se nombran se van a un borrador nuevo, con los mismos
   * datos, y en este quedan descartadas. Es la salida cuando la lectura de fotos juntó dos
   * productos en uno —dos diseños del álbum que vio iguales— o no repartió el conjunto (10 de
   * octubre de 2026).
   *
   * <p>El nuevo nace sin huella visual —la toma de su principal al aprobarse— y con una huella
   * derivada de la de este y de su primera foto ({@link HuellaProveedor#deParte}): con la misma,
   * aprobar los dos chocaría. Las fotos subidas desde el panel se quedan aquí. Este olvida su
   * huella visual, porque podía ser de una de las fotos que se van.
   *
   * @param fotos las que se lleva el nuevo, de las de la publicación que este borrador conserva
   * @param fotosDeLaPublicacion las de la publicación, en orden
   * @return el borrador nuevo, en revisión
   */
  public BorradorProducto partir(List<UUID> fotos, List<UUID> fotosDeLaPublicacion, Instant ahora) {
    exigirEnRevision("partir");
    Objects.requireNonNull(fotos, "Partir dice qué fotos se van.");
    List<UUID> propias =
        fotosDeLaPublicacion.stream().filter(id -> !fotosDescartadas.contains(id)).toList();
    Set<UUID> queSeVan = new LinkedHashSet<>(fotos);
    if (queSeVan.isEmpty()) {
      throw new ExcepcionDeDominio("Partir un borrador se lleva al menos una foto.");
    }
    if (!propias.containsAll(queSeVan)) {
      throw new ExcepcionDeDominio("Solo se pueden llevar fotos que este borrador conserva.");
    }
    if (queSeVan.size() == propias.size() && fotosSubidas.isEmpty()) {
      throw new ExcepcionDeDominio("Partir deja al menos una foto en el borrador de origen.");
    }
    UUID primera = propias.stream().filter(queSeVan::contains).findFirst().orElseThrow();
    Set<UUID> ajenasDelNuevo = new LinkedHashSet<>(fotosDeLaPublicacion);
    ajenasDelNuevo.removeAll(queSeVan);
    Map<UUID, String> tonosDelNuevo = new LinkedHashMap<>();
    tonosSugeridos.forEach(
        (foto, tono) -> {
          if (queSeVan.contains(foto)) {
            tonosDelNuevo.put(foto, tono);
          }
        });
    // Las mismas alertas, `SIN_FOTOS` incluida: si el origen la llevaba, ninguna de las fotos que
    // se van tiene archivo, y las subidas desde el panel se quedan en el origen.
    Set<AlertaBorrador> alertasDelNuevo = EnumSet.noneOf(AlertaBorrador.class);
    alertasDelNuevo.addAll(alertas);
    BorradorProducto nuevo =
        new BorradorProducto(
            GeneradorIdentificador.nuevo(),
            publicacionId,
            proveedorId,
            extraccionCruda,
            titulo,
            linea,
            tipo,
            precioProveedor,
            precioVentaSugerido,
            tallas,
            cantidadTonos,
            tonosNombrados,
            material,
            descripcion,
            altEn,
            huella == null ? null : HuellaProveedor.deParte(huella, primera.toString()),
            null,
            alertasDelNuevo,
            ajenasDelNuevo,
            List.of(),
            tallasPorTono,
            preciosAdicionales,
            tonosDelNuevo,
            lecturaDeFotos,
            List.of(),
            EstadoBorrador.EN_REVISION,
            null,
            null,
            ahora);
    // Sin la etiqueta: quien parte dice que no son el mismo producto, y el panel no tiene por qué
    // sugerirle volver a unirlos. El set y el ajuste de talla sí: las dos partes salen del mismo
    // anuncio y del mismo costo.
    nuevo.set = set;
    nuevo.ajusteDeTalla = ajusteDeTalla;
    fotosDescartadas.addAll(queSeVan);
    queSeVan.forEach(tonosSugeridos::remove);
    this.pHash = null;
    return nuevo;
  }

  /**
   * Quien revisa sube una foto porque la ingesta no trajo ninguna, o no las que sirven. Con ella el
   * borrador deja de estar {@link AlertaBorrador#SIN_FOTOS}: la alerta decía que no había con qué
   * publicarlo, y eso ya no es cierto.
   */
  public void agregarFotoSubida(FotoSubida foto) {
    exigirEnRevision("subir fotos a");
    Objects.requireNonNull(foto, "La foto no puede ser nula.");
    if (fotosSubidas.stream().anyMatch(f -> f.id().equals(foto.id()))) {
      throw new ExcepcionDeDominio("Esa foto ya está en el borrador.");
    }
    fotosSubidas.add(foto);
    alertas.remove(AlertaBorrador.SIN_FOTOS);
  }

  /**
   * El borrador se quedó sin ninguna foto con la que aprobarlo: quien revisa quitó la última. Es la
   * misma alerta que deja la ingesta, y la quita la próxima foto que se suba.
   */
  public void alertarSinFotos() {
    exigirEnRevision("alertar");
    alertas.add(AlertaBorrador.SIN_FOTOS);
  }

  /**
   * Quita una foto que se subió desde el panel. A diferencia de {@link #descartarFoto}, esta sí es
   * solo del borrador: quien llama borra el archivo con la referencia que se devuelve.
   */
  public FotoSubida quitarFotoSubida(UUID fotoId) {
    exigirEnRevision("quitar fotos de");
    FotoSubida foto =
        buscarFotoSubida(fotoId)
            .orElseThrow(() -> new ExcepcionDeDominio("Esa foto no se subió a este borrador."));
    fotosSubidas.remove(foto);
    return foto;
  }

  public Optional<FotoSubida> buscarFotoSubida(UUID fotoId) {
    return fotosSubidas.stream().filter(f -> f.id().equals(fotoId)).findFirst();
  }

  /**
   * La huella visual salió de la primera foto de la publicación; si quien revisa la descarta, esa
   * huella es de algo que no es este producto. Se olvida, y al aprobar se calcula de la principal
   * que la persona deje.
   */
  public void olvidarHuellaVisual() {
    exigirEnRevision("olvidar la huella visual de");
    this.pHash = null;
  }

  /**
   * @param pHashDeLaPrincipal el de la foto que quien aprueba eligió como principal, o nulo si no
   *     se pudo calcular. Solo se usa cuando el borrador no trae uno: el de un mensaje con varios
   *     productos nace sin huella visual, porque la primera foto podía ser de cualquiera de ellos,
   *     y es aquí donde una persona dice cuál es de este.
   */
  public void aprobar(UUID productoId, PHash pHashDeLaPrincipal) {
    exigirEnRevision("aprobar");
    this.productoId = Objects.requireNonNull(productoId, "Aprobar es crear un producto.");
    this.estado = EstadoBorrador.APROBADO;
    if (this.pHash == null) {
      this.pHash = pHashDeLaPrincipal;
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

  public boolean tieneAlertas() {
    return !alertas.isEmpty();
  }

  /** Nunca con alertas. Y hoy nunca: la publicación automática no está encendida para nadie. */
  public boolean esPublicableAutomaticamente() {
    return estado == EstadoBorrador.EN_REVISION && alertas.isEmpty();
  }

  private void exigirEnRevision(String accion) {
    if (estado != EstadoBorrador.EN_REVISION) {
      throw new ExcepcionDeDominio(
          "Solo se puede " + accion + " un borrador en revisión; este está " + estado + ".");
    }
  }

  private static String enBlancoEsNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  public UUID id() {
    return id;
  }

  public UUID publicacionId() {
    return publicacionId;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  public String extraccionCruda() {
    return extraccionCruda;
  }

  public Optional<String> titulo() {
    return Optional.ofNullable(titulo);
  }

  public Optional<LineaCatalogo> linea() {
    return Optional.ofNullable(linea);
  }

  public TipoProductoProveedor tipo() {
    return tipo;
  }

  public Optional<Dinero> precioProveedor() {
    return Optional.ofNullable(precioProveedor);
  }

  /** El precio de contado sugerido; ver el campo. */
  public Optional<Dinero> precioVentaSugerido() {
    return Optional.ofNullable(precioVentaSugerido);
  }

  public Tallas tallas() {
    return tallas;
  }

  public Optional<Integer> cantidadTonos() {
    return Optional.ofNullable(cantidadTonos);
  }

  public List<String> tonosNombrados() {
    return tonosNombrados;
  }

  public Optional<String> material() {
    return Optional.ofNullable(material);
  }

  /** Lo que la ficha va a decir del producto. Sin ella el borrador no se aprueba. */
  public Optional<String> descripcion() {
    return Optional.ofNullable(descripcion);
  }

  /** El título en inglés, que el panel propone como texto alternativo de las fotos. */
  public Optional<String> altEn() {
    return Optional.ofNullable(altEn);
  }

  public Optional<HuellaProveedor> huella() {
    return Optional.ofNullable(huella);
  }

  public Optional<PHash> pHash() {
    return Optional.ofNullable(pHash);
  }

  public Set<AlertaBorrador> alertas() {
    return Set.copyOf(alertas);
  }

  /** Las fotos de la publicación que quien revisa sacó de este borrador, por mensaje. */
  public Set<UUID> fotosDescartadas() {
    return Set.copyOf(fotosDescartadas);
  }

  /** Las que quien revisa subió desde el panel, en el orden en que llegaron. */
  public List<FotoSubida> fotosSubidas() {
    return List.copyOf(fotosSubidas);
  }

  public TallasPorTono tallasPorTono() {
    return tallasPorTono;
  }

  public List<PrecioAdicional> preciosAdicionales() {
    return preciosAdicionales;
  }

  /** El tono que la lectura de fotos vio en cada foto de un solo color, por mensaje. */
  public Map<UUID, String> tonosSugeridos() {
    return Map.copyOf(tonosSugeridos);
  }

  public Optional<String> lecturaDeFotos() {
    return Optional.ofNullable(lecturaDeFotos);
  }

  /** Las de otras publicaciones que se sumaron, por mensaje y en el orden en que llegaron. */
  public List<UUID> fotosAgregadas() {
    return List.copyOf(fotosAgregadas);
  }

  public EstadoBorrador estado() {
    return estado;
  }

  public Optional<UUID> productoId() {
    return Optional.ofNullable(productoId);
  }

  public Optional<String> motivoRechazo() {
    return Optional.ofNullable(motivoRechazo);
  }

  public Instant creadoEn() {
    return creadoEn;
  }
}
