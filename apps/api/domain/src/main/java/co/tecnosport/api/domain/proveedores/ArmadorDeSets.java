package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte en set la prenda que el anuncio ofrece con su acompañante (decidido por el negocio el
 * 12 de octubre de 2026, solo para los proveedores que venden sets: hoy La Riverah, {@link
 * Proveedor#armaSets()}).
 *
 * <p>Hasta ese día «Falda $49.900 / Set falda + básica $88.000» era la falda, y el set quedaba en
 * {@link PrecioAdicional} para que alguien lo viera. Ahora el borrador es el set:
 *
 * <ul>
 *   <li><b>La falda con su blusa es un conjunto</b> ({@link TipoProductoProveedor#CONJUNTO_FALDA},
 *       y lo mismo un short o un pantalón con su blusa), que se publica en Conjuntos.
 *   <li><b>La camiseta o el polo con su gorra es un set</b> que conserva el tipo de la prenda: se
 *       publica en Camisetas o en Polos, donde lo busca quien quiere una camiseta. La gorra es la
 *       de la foto de ese SKU.
 *   <li><b>La descripción nombra la gorra o la blusa</b>: la del set si el modelo la escribió; si
 *       no, la de la prenda con una frase que dice con qué se vende ({@link #describirSet}).
 *   <li><b>El costo es el precio del set si el proveedor lo anuncia</b> —el combo—; si no, la suma
 *       de la prenda y su acompañante: «Falda $59.900 / Básica 48.000» cuesta 107.900. Sobre ese
 *       costo se calcula después el precio de venta, como con cualquier otro.
 * </ul>
 *
 * <p>Lo demás se queda como estaba: una promoción por cantidad no arma nada, un acompañante que no
 * es ni blusa ni gorra tampoco, una blusa con una prenda de arriba no es un conjunto, y sin precio
 * de la prenda no hay suma que hacer. En todos esos casos el borrador sigue siendo la prenda sola.
 *
 * <p>La aritmética vive aquí y no en el modelo: el extractor solo dice qué clase de precio es cada
 * uno y qué prenda acompaña.
 */
public final class ArmadorDeSets {

  /**
   * Lo que una gorra convierte en set, y conserva su tipo: la camiseta y el polo, y desde el 29 de
   * octubre de 2026 la pantaloneta y la bermuda —«Pantaloneta $39.990 / Gorra 35.000 / Dúo $69.900»
   * quedaba como la pantaloneta sola (ADR-0101)—.
   */
  private static final Set<TipoProductoProveedor> CON_GORRA_SON_SET =
      EnumSet.of(
          TipoProductoProveedor.CAMISETA,
          TipoProductoProveedor.POLO,
          TipoProductoProveedor.SHORT,
          TipoProductoProveedor.BERMUDA);

  /** Los conjuntos que el anuncio ya vende como uno: no esperan acompañante. */
  private static final Set<TipoProductoProveedor> CONJUNTOS =
      EnumSet.of(
          TipoProductoProveedor.CONJUNTO_FALDA,
          TipoProductoProveedor.CONJUNTO_PANTALON,
          TipoProductoProveedor.CONJUNTO_SHORT);

  /** La palabra con que el proveedor nombra el combo: «Dúo $69.900», «Set falda + básica». */
  private static final Pattern PALABRA_DEL_COMBO =
      Pattern.compile(
          "^\\s*(sets?|conjuntos?|d[uú]os?|combos?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** El combo que nombra primero el acompañante: «Set básica + falda», «Dúo gorra y camiseta». */
  private static final Pattern ACOMPANANTE_DELANTE =
      Pattern.compile(
          "^\\s*(?:sets?|conjuntos?|d[uú]os?|combos?)?[^\\p{L}]*"
              + "(?:b[aá]sicas?|blusas?|gorras?|cachuchas?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Lo que una blusa convierte en conjunto: la prenda de abajo. */
  private static final Set<TipoProductoProveedor> CON_BLUSA_SON_CONJUNTO =
      EnumSet.of(
          TipoProductoProveedor.FALDA,
          TipoProductoProveedor.FALDA_SHORT,
          TipoProductoProveedor.SHORT,
          TipoProductoProveedor.PANTALON);

  /**
   * @param producto el producto como quedó después del set: título, tipo y textos del set
   * @param costo el precio del proveedor con el que se sigue: el del set o la suma
   * @param preciosLeidos los que el modelo leyó y dieron el costo: el del combo, o el de la prenda
   *     y el de su acompañante
   * @param conDescripcionDelSet la descripción nombra la gorra o la blusa, la haya escrito el
   *     modelo o se haya completado; si no, no había descripción de la prenda que completar, y
   *     quien revisa tiene que escribirla
   */
  public record SetArmado(
      ProductoExtraido producto,
      Dinero costo,
      ArmadoDeSet armado,
      List<Dinero> preciosLeidos,
      boolean conDescripcionDelSet) {

    public SetArmado {
      Objects.requireNonNull(producto);
      Objects.requireNonNull(costo);
      Objects.requireNonNull(armado);
      preciosLeidos = preciosLeidos == null ? List.of() : List.copyOf(preciosLeidos);
    }

    /**
     * ¿Están escritos en el anuncio todos los precios que dieron el costo? El de la prenda pasa por
     * el contraste con el texto; los del combo y el acompañante, hasta que existió el set, solo se
     * mostraban, y ahora son el costo.
     */
    public boolean preciosEscritosEn(String texto) {
      return preciosLeidos.stream().allMatch(p -> estaEscrito(p, texto));
    }
  }

  /** ¿Está este precio escrito en el texto? Ver {@link PrecioEscrito}. */
  public static boolean estaEscrito(Dinero precio, String texto) {
    return PrecioEscrito.en(precio, texto);
  }

  /**
   * Lo que en el texto anuncia un acompañante o un combo: la gorra, el set, el dúo. La «básica»
   * solo con una prenda de abajo, porque la «BÁSICA RAYA AMERICANAS» es la prenda misma.
   */
  private static final Pattern ANUNCIA_ACOMPANANTE =
      Pattern.compile(
          "(?<!\\p{L})(?:gorras?|cachuchas?|sets?|d[uú]o)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern ANUNCIA_BASICA =
      Pattern.compile(
          "(?<!\\p{L})b[aá]sicas?(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /**
   * ¿El texto ofrece la prenda con un acompañante que el modelo no devolvió? El segundo anuncio de
   * camisetas oversize de La Riverah, idéntico al primero, salió sin la nota de las gorras y sin
   * set (12 de octubre de 2026). No se arma nada con esto, solo se alerta: el precio lo tendría que
   * inventar.
   */
  public static boolean anunciaAcompanante(String texto, TipoProductoProveedor tipo) {
    // «Set Blusa y falda» a un solo precio es el producto, no una prenda con acompañante.
    if (texto == null || CONJUNTOS.contains(tipo)) {
      return false;
    }
    if (ANUNCIA_ACOMPANANTE.matcher(texto).find()) {
      return true;
    }
    return CON_BLUSA_SON_CONJUNTO.contains(tipo) && ANUNCIA_BASICA.matcher(texto).find();
  }

  /**
   * El nombre del set en inglés cuando el modelo no lo escribió: el de la prenda sin el género y el
   * acompañante —«Oversized T-shirt for men» con gorra es «Oversized T-shirt and cap set»—.
   */
  static String altDeSet(String altDeLaPrenda, PrendaAcompanante acompanante) {
    if (altDeLaPrenda == null || altDeLaPrenda.isBlank()) {
      return null;
    }
    String prenda = GENERO_EN.matcher(altDeLaPrenda).replaceAll("").replaceAll("\\s+", " ").strip();
    return switch (acompanante) {
      case GORRA -> prenda + " and cap set";
      case BLUSA -> prenda + " and blouse set";
      case OTRA -> null;
    };
  }

  private static final Pattern GENERO_EN =
      Pattern.compile(
          "(?:\\s+for\\s+(?:men|women|ladies|man|woman))|(?:^(?:men's|women's|ladies')\\s+)",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern NOMBRA_GORRA =
      Pattern.compile(
          "(?<!\\p{L})(?:gorras?|cachuchas?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern NOMBRA_BLUSA =
      Pattern.compile(
          "(?<!\\p{L})(?:blusas?|b[aá]sicas?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /**
   * La descripción del set cuando el modelo no la escribió: la de la prenda y una frase que dice
   * con qué se vende. En los dos anuncios de camisetas oversize de La Riverah del 12 de octubre de
   * 2026 el modelo no la escribió nunca, ni siquiera en el que leyó la gorra, y los dos borradores
   * salieron con la descripción de la camiseta sola. Si la de la prenda ya nombra la gorra o la
   * blusa, se deja como está.
   *
   * @return nulo si la prenda no tiene descripción: no hay nada que completar
   */
  static String describirSet(String descripcionDeLaPrenda, PrendaAcompanante acompanante) {
    if (descripcionDeLaPrenda == null || descripcionDeLaPrenda.isBlank()) {
      return null;
    }
    String prenda = descripcionDeLaPrenda.strip();
    String frase =
        switch (acompanante) {
          case GORRA ->
              NOMBRA_GORRA.matcher(prenda).find()
                  ? null
                  : "Se vende en set con la gorra que aparece en las fotos.";
          case BLUSA ->
              NOMBRA_BLUSA.matcher(prenda).find()
                  ? null
                  : "Se vende en conjunto con la blusa que aparece en las fotos.";
          case OTRA -> null;
        };
    if (frase == null) {
      return prenda;
    }
    return (prenda.matches("(?s).*[.!?]$") ? prenda : prenda + ".") + " " + frase;
  }

  private ArmadorDeSets() {}

  /**
   * @param precioDeLaPrenda el precio con el que se seguiría sin set: el del texto, contrastado;
   *     nulo si el anuncio no trae ninguno
   * @return el set, o vacío si el anuncio no ofrece la prenda con una blusa o una gorra
   */
  public static Optional<SetArmado> armar(ProductoExtraido producto, Dinero precioDeLaPrenda) {
    Objects.requireNonNull(producto, "No hay set sin producto.");
    // El agotado también se arma: su huella tiene que ser la del set para agotar el set.
    if (!producto.esProducto()) {
      return Optional.empty();
    }
    Optional<PrecioAdicional> combo =
        producto.preciosAdicionales().stream()
            .filter(p -> p.clase() == ClaseDePrecioAdicional.COMBO && armaSet(p))
            .findFirst();
    List<PrecioAdicional> acompanantes =
        producto.preciosAdicionales().stream()
            .filter(p -> p.clase() == ClaseDePrecioAdicional.ACOMPANANTE && armaSet(p))
            .toList();
    Optional<PrendaAcompanante> prenda =
        combo
            .map(PrecioAdicional::prenda)
            .or(() -> acompanantes.stream().map(PrecioAdicional::prenda).findFirst());
    if (prenda.isEmpty()) {
      return Optional.empty();
    }
    Optional<TipoProductoProveedor> tipo = tipoDelSet(producto.tipo(), prenda.get());
    if (tipo.isEmpty()) {
      return Optional.empty();
    }

    Dinero costo;
    OrigenDelCostoDeSet origen;
    List<Dinero> leidos;
    if (combo.isPresent()) {
      costo = combo.get().precio();
      origen = OrigenDelCostoDeSet.ANUNCIADO;
      leidos = List.of(costo);
    } else if (precioDeLaPrenda != null) {
      // Un solo acompañante: la gorra del set es una. Si el modelo la repite, o el anuncio trae
      // dos gorras a precios distintos, sumarlas todas inflaría el costo.
      Dinero delAcompanante =
          acompanantes.stream()
              .filter(a -> a.prenda() == prenda.get())
              .findFirst()
              .orElseThrow()
              .precio();
      costo = Dinero.deCop(precioDeLaPrenda.valor().add(delAcompanante.valor()));
      origen = OrigenDelCostoDeSet.SUMA_DE_PRENDAS;
      leidos = List.of(precioDeLaPrenda, delAcompanante);
    } else {
      return Optional.empty();
    }

    List<PrecioAdicional> adicionales = new ArrayList<>();
    if (precioDeLaPrenda != null && producto.titulo() != null) {
      // La prenda sola, para que quien revisa siga viendo cuánto cuesta sin su acompañante.
      adicionales.add(
          new PrecioAdicional(
              producto.titulo(), precioDeLaPrenda, ClaseDePrecioAdicional.PRENDA_SOLA, null));
    }
    adicionales.addAll(producto.preciosAdicionales());

    Optional<TextosDelSet> textos = producto.setOpcional();
    String descripcion =
        textos
            .map(TextosDelSet::descripcion)
            .orElseGet(() -> describirSet(producto.descripcion(), prenda.get()));
    ProductoExtraido comoSet =
        new ProductoExtraido(
            producto.esProducto(),
            producto.estaAgotado(),
            TituloDeSet.componer(
                producto.titulo(),
                prenda.get(),
                combo.flatMap(c -> palabraDelCombo(c.concepto())).orElse(null),
                combo.map(c -> ACOMPANANTE_DELANTE.matcher(c.concepto()).find()).orElse(false)),
            producto.linea(),
            tipo.get(),
            costo,
            producto.tallas(),
            producto.cantidadTonos(),
            producto.tonosNombrados(),
            producto.material(),
            descripcion,
            textos
                .map(TextosDelSet::altEn)
                .orElseGet(() -> altDeSet(producto.altEn(), prenda.get())),
            producto.esReplica(),
            producto.confianza(),
            producto.notas(),
            producto.codigoReferencia(),
            producto.tallasPorTono(),
            adicionales,
            producto.marcas(),
            producto.set());
    return Optional.of(
        new SetArmado(
            comoSet, costo, new ArmadoDeSet(prenda.get(), origen), leidos, descripcion != null));
  }

  /** «Dúo», «Set», «Conjunto» o «Combo», como el proveedor nombra su combo; vacío si no. */
  static Optional<String> palabraDelCombo(String concepto) {
    if (concepto == null) {
      return Optional.empty();
    }
    Matcher m = PALABRA_DEL_COMBO.matcher(concepto);
    if (!m.find()) {
      return Optional.empty();
    }
    String palabra = m.group(1).toLowerCase(Locale.ROOT);
    if (palabra.startsWith("d")) {
      return Optional.of("Dúo");
    }
    if (palabra.startsWith("conjunto")) {
      return Optional.of("Conjunto");
    }
    return Optional.of(palabra.startsWith("combo") ? "Combo" : "Set");
  }

  private static boolean armaSet(PrecioAdicional precio) {
    return precio.prenda() == PrendaAcompanante.BLUSA || precio.prenda() == PrendaAcompanante.GORRA;
  }

  private static Optional<TipoProductoProveedor> tipoDelSet(
      TipoProductoProveedor tipo, PrendaAcompanante prenda) {
    if (tipo == TipoProductoProveedor.OTRO) {
      return Optional.empty();
    }
    if (prenda == PrendaAcompanante.GORRA) {
      return CON_GORRA_SON_SET.contains(tipo) ? Optional.of(tipo) : Optional.empty();
    }
    if (!CON_BLUSA_SON_CONJUNTO.contains(tipo)) {
      return Optional.empty();
    }
    return Optional.of(
        switch (tipo) {
          case SHORT -> TipoProductoProveedor.CONJUNTO_SHORT;
          case PANTALON -> TipoProductoProveedor.CONJUNTO_PANTALON;
          default -> TipoProductoProveedor.CONJUNTO_FALDA;
        });
  }
}
