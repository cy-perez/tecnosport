package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Una línea mayorista que manda su surtido por WhatsApp.
 *
 * <p>Lo que identifica al proveedor en una exportación de chat no es su teléfono sino <b>el nombre
 * con que WhatsApp lo escribe</b> delante de cada mensaje, que es el nombre con que está guardado
 * en el contacto del celular del negocio. Por eso {@code nombreEnExportacion} es un dato aparte de
 * {@code nombre}: el primero lo dicta el teléfono y el segundo lo elige quien administra. El
 * teléfono sí importa, y por eso se exige desde ya: es la llave con la que la API de WhatsApp
 * identifica al remitente cuando la ingesta deje de ser por exportación.
 *
 * <h2>Solo bolsos y ropa</h2>
 *
 * <p>La ingesta por mensajes cubre las líneas que llegan así; la tecnología sigue entrando por
 * listas ({@code tools/cargar-catalogo.mjs}). Dejar entrar un proveedor de otra línea sería
 * prometer un flujo que no existe.
 *
 * <h2>El factor de margen es del proveedor o de la línea, nunca de los dos</h2>
 *
 * <p>Vacío quiere decir «el de su línea», que vive en configuración. Un factor por debajo de uno no
 * es una decisión sino un error al teclear —0,35 donde iba 1,35—, y venderlo por debajo del costo
 * no lo arregla ningún borrador: se rechaza aquí.
 *
 * <h2>El orden en que publica</h2>
 *
 * <p>Si manda primero las fotos o primero el texto con el precio. Es obligatorio y no tiene un
 * valor que el dominio suponga: lo decide quien registra al proveedor mirando su chat, y solo
 * cambia cómo se reparten las fotos de un mismo minuto ({@link OrdenDePublicacion}).
 */
public final class Proveedor {

  /** Las dos marcas invisibles que la exportacion de Android mete delante del nombre. */
  static final String MARCA_DE_DIRECCION = String.valueOf((char) 0x200E);

  static final String ESPACIO_ANGOSTO = String.valueOf((char) 0x202F);

  /** El de la columna, como el del nombre principal. */
  static final int LARGO_MAXIMO_DE_REMITENTE = 120;

  /**
   * Las líneas que llegan por la exportación del chat: el extractor, el factor de margen y la
   * huella visual son de bolsos, de ropa y de calzado. La tecnología no entra por ahí.
   *
   * <p>El calzado entró el 08/10/2026: el extractor ya lo reconocía —tenis, tallas de 1 en 1— pero
   * no había cómo registrar a quien lo surte. Su factor de margen por omisión es el de la ropa
   * mientras el negocio no defina uno propio ({@code PROVEEDORES_MARGEN_CALZADO}).
   */
  public static final Set<LineaCatalogo> LINEAS_POR_EXPORTACION =
      EnumSet.of(LineaCatalogo.BOLSOS, LineaCatalogo.ROPA, LineaCatalogo.CALZADO);

  /**
   * Las líneas de un proveedor. La tecnología se sumó el 08/10/2026: su proveedor manda listas de
   * precios, que procesa la skill `listas-de-proveedor` y se importan como borradores de
   * tecnología, no como exportaciones del chat.
   */
  public static final Set<LineaCatalogo> LINEAS_ADMITIDAS =
      EnumSet.of(
          LineaCatalogo.BOLSOS,
          LineaCatalogo.ROPA,
          LineaCatalogo.CALZADO,
          LineaCatalogo.TECNOLOGIA);

  private final UUID id;
  private String nombre;
  private LineaCatalogo linea;
  private String telefonoWhatsApp;
  private String nombreEnExportacion;
  private boolean activo;
  private boolean publicacionAutomatica;
  private BigDecimal factorDeMargen;
  private OrdenDePublicacion ordenDePublicacion;

  /**
   * Si sube sus dos chats —el general y el de caballero— en un solo zip, con un {@code .txt} por
   * chat. Meraki publica desde el mismo número en los dos (9 de octubre de 2026): con un solo zip
   * el chat de caballero se procesa siempre primero, y el general descarta lo que aquel ya trajo.
   */
  private boolean dosChatsEnUnZip;

  /**
   * Los tipos de prenda que no se le compran a este proveedor. Un anuncio que trae uno se descarta
   * entero, aunque venga con otras prendas: a Violeta no se le compran pantalones, sudaderas ni
   * shorts, y el anuncio de una blusa con su jean no deja ni la blusa (12 de octubre de 2026).
   */
  private Set<TipoProductoProveedor> tiposExcluidos = Set.of();

  /**
   * Si la prenda que anuncia con su acompañante —la falda con la blusa, la camiseta con la gorra—
   * se publica como set y no sola ({@link ArmadorDeSets}). Decidido por el negocio el 12 de octubre
   * de 2026 solo para La Riverah: a los demás se les sigue comprando la prenda.
   */
  private boolean armaSets;

  /**
   * Si surte desde Medellín: lo que vende llega al punto de TecnoSport a tiempo para recogerlo.
   * Decidido por el negocio el 26 de octubre de 2026 para La Riverah y Violeta; un pedido se puede
   * recoger en el punto solo si todo lo que lleva viene de un proveedor local. Vale para cualquier
   * línea: que sea local lo dice dónde está, no cómo manda su surtido.
   */
  private boolean local;

  /**
   * Los nombres de las otras cuentas desde las que publica en el mismo chat. Meraki publica también
   * como «Merakiapartados», y del 16 al 18 de octubre de 2026 tres conjuntos llegaron solo desde
   * esa cuenta: la ingesta los ignoraba (28 de octubre de 2026). Vacía en casi todos.
   */
  private List<String> otrosRemitentes = List.of();

  /** Lo que su chat tiene de distinto: ver {@link AjustesDeIngesta}. */
  private AjustesDeIngesta ajustesDeIngesta = AjustesDeIngesta.POR_OMISION;

  public Proveedor(
      UUID id,
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      boolean activo,
      boolean publicacionAutomatica,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion) {
    this.id = Objects.requireNonNull(id, "El id del proveedor no puede ser nulo.");
    this.nombre = exigirTexto(nombre, "El proveedor necesita un nombre.");
    this.linea = exigirLinea(linea);
    this.telefonoWhatsApp =
        exigirTexto(telefonoWhatsApp, "El proveedor necesita el teléfono de WhatsApp.");
    this.nombreEnExportacion =
        exigirTexto(
            nombreEnExportacion,
            "El proveedor necesita el nombre tal como aparece en la exportación del chat.");
    this.activo = activo;
    this.publicacionAutomatica = publicacionAutomatica;
    this.factorDeMargen = exigirFactor(factorDeMargen);
    this.ordenDePublicacion = exigirOrden(ordenDePublicacion);
  }

  /** Nace activo y sin publicación automática: publicar solo lo decide una persona por ahora. */
  public static Proveedor crear(
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion) {
    return new Proveedor(
        GeneradorIdentificador.nuevo(),
        nombre,
        linea,
        telefonoWhatsApp,
        nombreEnExportacion,
        true,
        false,
        factorDeMargen,
        ordenDePublicacion);
  }

  public void editar(
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      boolean activo,
      boolean publicacionAutomatica,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion) {
    this.nombre = exigirTexto(nombre, "El proveedor necesita un nombre.");
    this.linea = exigirLinea(linea);
    this.telefonoWhatsApp =
        exigirTexto(telefonoWhatsApp, "El proveedor necesita el teléfono de WhatsApp.");
    this.nombreEnExportacion =
        exigirTexto(
            nombreEnExportacion,
            "El proveedor necesita el nombre tal como aparece en la exportación del chat.");
    this.activo = activo;
    this.publicacionAutomatica = publicacionAutomatica;
    this.factorDeMargen = exigirFactor(factorDeMargen);
    this.ordenDePublicacion = exigirOrden(ordenDePublicacion);
  }

  /**
   * ¿Este remitente es el proveedor? WhatsApp escribe el nombre del contacto tal cual, pero entre
   * lo que exporta Android y lo que exporta iOS cambian los espacios y aparecen marcas invisibles
   * de dirección de texto; comparar carácter por carácter fallaría por eso y no por nada real.
   *
   * <p>Y en un grupo o en el canal de avisos de una comunidad —que es donde los proveedores de
   * verdad publican— el remitente no siempre es el nombre del contacto: si no está guardado,
   * WhatsApp escribe {@code ~ Nombre} con el nombre que esa persona se puso, o el número con el
   * indicativo y espacios. Se acepta cualquiera de las tres formas: el nombre configurado con o sin
   * la virgulilla, y el número del proveedor comparando solo los dígitos. Y el de sus otras cuentas
   * ({@link #otrosRemitentes()}), igual que el principal.
   */
  public boolean esRemitente(String remitente) {
    if (remitente == null) {
      return false;
    }
    String normalizado = normalizar(remitente);
    if (normalizado.equals(normalizar(nombreEnExportacion))
        || otrosRemitentes.stream().anyMatch(otro -> normalizado.equals(normalizar(otro)))) {
      return true;
    }
    String digitos = soloDigitos(normalizado);
    return !digitos.isEmpty() && digitos.equals(soloDigitos(telefonoWhatsApp));
  }

  private static String normalizar(String nombre) {
    String limpio = nombre.replace(MARCA_DE_DIRECCION, "").replace(ESPACIO_ANGOSTO, " ").strip();
    // La virgulilla con la que WhatsApp marca a quien no está en los contactos.
    if (limpio.startsWith("~")) {
      limpio = limpio.substring(1).strip();
    }
    return limpio.toLowerCase();
  }

  /** Un número solo compara por sus dígitos: "+57 300 123 4567" y "573001234567" son el mismo. */
  private static String soloDigitos(String texto) {
    StringBuilder digitos = new StringBuilder();
    for (char c : texto.toCharArray()) {
      if (Character.isDigit(c)) {
        digitos.append(c);
      }
    }
    // Un nombre con un número dentro ("Bolsos 24") no es un teléfono.
    return digitos.length() >= 7 ? digitos.toString() : "";
  }

  private static String exigirTexto(String valor, String mensaje) {
    if (valor == null || valor.isBlank()) {
      throw new ExcepcionDeDominio(mensaje);
    }
    return valor.strip();
  }

  private static LineaCatalogo exigirLinea(LineaCatalogo linea) {
    if (linea == null || !LINEAS_ADMITIDAS.contains(linea)) {
      throw new ExcepcionDeDominio(
          "Un proveedor puede ser de bolsos, de ropa, de calzado o de tecnología.");
    }
    return linea;
  }

  /** Si sus productos llegan por la exportación del chat; los de tecnología llegan por listas. */
  public boolean entraPorExportacion() {
    return LINEAS_POR_EXPORTACION.contains(linea);
  }

  /**
   * Si su catálogo llega por la lista de precios que procesa la skill (ADR-0075). Es lo contrario
   * de {@link #entraPorExportacion()}, dicho aparte para que quien pregunta por la lista no tenga
   * que saber qué líneas existen.
   */
  public boolean entraPorLista() {
    return !entraPorExportacion();
  }

  /**
   * Marca o desmarca que sube sus dos chats en un solo zip. Solo quien entra por la exportación del
   * chat tiene chats que subir; el adaptador lo usa también para devolverlo como estaba guardado.
   */
  public void definirDosChatsEnUnZip(boolean dosChats) {
    if (dosChats && !entraPorExportacion()) {
      throw new ExcepcionDeDominio(
          "Solo un proveedor que entra por la exportación del chat sube sus chats en un zip.");
    }
    this.dosChatsEnUnZip = dosChats;
  }

  public boolean subeDosChatsEnUnZip() {
    return dosChatsEnUnZip;
  }

  /**
   * Marca o desmarca que vende sets. Solo quien entra por la exportación del chat tiene anuncios
   * que armar; el adaptador lo usa también para devolverlo como estaba guardado.
   */
  public void definirArmaSets(boolean arma) {
    if (arma && !entraPorExportacion()) {
      throw new ExcepcionDeDominio(
          "Solo un proveedor que entra por la exportación del chat arma sets de sus anuncios.");
    }
    this.armaSets = arma;
  }

  public boolean armaSets() {
    return armaSets;
  }

  /** Marca o desmarca que surte desde Medellín; el adaptador lo usa también para devolverlo. */
  public void definirLocal(boolean local) {
    this.local = local;
  }

  public boolean esLocal() {
    return local;
  }

  /**
   * Define cómo se ingiere su chat. Solo quien entra por la exportación del chat tiene un chat que
   * ingerir, y solo el calzado tiene tallas por género; el adaptador lo usa también para devolverlo
   * como estaba guardado.
   */
  public void definirAjustesDeIngesta(AjustesDeIngesta ajustes) {
    AjustesDeIngesta definidos = ajustes == null ? AjustesDeIngesta.POR_OMISION : ajustes;
    if (!definidos.porOmision() && !entraPorExportacion()) {
      throw new ExcepcionDeDominio(
          "Solo un proveedor que entra por la exportación del chat tiene ajustes de ingesta.");
    }
    if (definidos.tallasPorGenero() != null && linea != LineaCatalogo.CALZADO) {
      throw new ExcepcionDeDominio("Solo un proveedor de calzado tiene tallas por género.");
    }
    this.ajustesDeIngesta = definidos;
  }

  public AjustesDeIngesta ajustesDeIngesta() {
    return ajustesDeIngesta;
  }

  /**
   * Define los tipos que no se le compran. {@code OTRO} no se puede excluir: es lo que el extractor
   * responde cuando no supo qué es, y excluirlo descartaría lo que nadie miró. Solo quien entra por
   * la exportación del chat tiene anuncios que descartar; el adaptador lo usa también para
   * devolverlos como estaban guardados.
   */
  public void definirTiposExcluidos(Set<TipoProductoProveedor> tipos) {
    Set<TipoProductoProveedor> excluidos =
        tipos == null || tipos.isEmpty()
            ? EnumSet.noneOf(TipoProductoProveedor.class)
            : EnumSet.copyOf(tipos);
    if (excluidos.contains(TipoProductoProveedor.OTRO)) {
      throw new ExcepcionDeDominio(
          "No se puede excluir el tipo «otro»: es el de los anuncios que el extractor no reconoció.");
    }
    if (excluidos.contains(TipoProductoProveedor.CONJUNTO_FALDA)) {
      // Lo arma la ingesta después de comparar con las exclusiones: excluirlo no descartaría nada.
      throw new ExcepcionDeDominio(
          "No se puede excluir el conjunto con falda: lo arma la ingesta, no lo dice el anuncio.");
    }
    if (!excluidos.isEmpty() && !entraPorExportacion()) {
      throw new ExcepcionDeDominio(
          "Solo un proveedor que entra por la exportación del chat puede excluir tipos de prenda.");
    }
    this.tiposExcluidos = Set.copyOf(excluidos);
  }

  /**
   * Define los otros nombres con que aparece en la exportación. Se guardan sin espacios a los
   * lados, sin vacíos y sin repetir —tampoco el nombre principal—, comparando como compara {@link
   * #esRemitente}.
   */
  public void definirOtrosRemitentes(Collection<String> nombres) {
    List<String> definidos = new ArrayList<>();
    List<String> vistos = new ArrayList<>(List.of(normalizar(nombreEnExportacion)));
    if (nombres != null) {
      for (String nombreDeOtro : nombres) {
        if (nombreDeOtro == null || nombreDeOtro.isBlank()) {
          continue;
        }
        String normalizado = normalizar(nombreDeOtro);
        if (normalizado.isEmpty() || vistos.contains(normalizado)) {
          continue;
        }
        if (nombreDeOtro.strip().length() > LARGO_MAXIMO_DE_REMITENTE) {
          throw new ExcepcionDeDominio(
              "Un nombre de remitente tiene como mucho "
                  + LARGO_MAXIMO_DE_REMITENTE
                  + " caracteres.");
        }
        vistos.add(normalizado);
        definidos.add(nombreDeOtro.strip());
      }
    }
    if (!definidos.isEmpty() && !entraPorExportacion()) {
      throw new ExcepcionDeDominio(
          "Solo un proveedor que entra por la exportación del chat tiene otros remitentes.");
    }
    this.otrosRemitentes = List.copyOf(definidos);
  }

  /** Vacía si publica solo desde la cuenta de {@link #nombreEnExportacion()}. */
  public List<String> otrosRemitentes() {
    return otrosRemitentes;
  }

  /** Vacío cuando se le compra todo lo que anuncia. */
  public Set<TipoProductoProveedor> tiposExcluidos() {
    return tiposExcluidos;
  }

  /** Si el tipo es uno de los que no se le compran. */
  public boolean excluye(TipoProductoProveedor tipo) {
    return tipo != null && tiposExcluidos.contains(tipo);
  }

  private static OrdenDePublicacion exigirOrden(OrdenDePublicacion orden) {
    if (orden == null) {
      throw new ExcepcionDeDominio(
          "El proveedor necesita el orden en que publica: primero las fotos o primero el texto.");
    }
    return orden;
  }

  private static BigDecimal exigirFactor(BigDecimal factor) {
    if (factor == null) {
      return null;
    }
    if (factor.compareTo(BigDecimal.ONE) < 0) {
      throw new ExcepcionDeDominio(
          "El factor de margen no puede ser menor que 1: vendería por debajo del costo.");
    }
    return factor;
  }

  public UUID id() {
    return id;
  }

  public String nombre() {
    return nombre;
  }

  public LineaCatalogo linea() {
    return linea;
  }

  public String telefonoWhatsApp() {
    return telefonoWhatsApp;
  }

  public String nombreEnExportacion() {
    return nombreEnExportacion;
  }

  public boolean activo() {
    return activo;
  }

  public boolean publicacionAutomatica() {
    return publicacionAutomatica;
  }

  /** Vacío cuando manda el factor de la línea. */
  public Optional<BigDecimal> factorDeMargen() {
    return Optional.ofNullable(factorDeMargen);
  }

  public OrdenDePublicacion ordenDePublicacion() {
    return ordenDePublicacion;
  }
}
