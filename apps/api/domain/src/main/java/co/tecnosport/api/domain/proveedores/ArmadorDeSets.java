package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  /** Lo que una gorra convierte en set: la camiseta y el polo, que conservan su tipo. */
  private static final Set<TipoProductoProveedor> CON_GORRA_SON_SET =
      EnumSet.of(TipoProductoProveedor.CAMISETA, TipoProductoProveedor.POLO);

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
   */
  public record SetArmado(
      ProductoExtraido producto, Dinero costo, ArmadoDeSet armado, List<Dinero> preciosLeidos) {

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

  private static final Pattern NO_CUENTA = Pattern.compile("[\\s.,$]+");

  /**
   * ¿Está este precio escrito en el texto? Completo —«88.000», «$ 88.000», «88000»— o en miles con
   * la palabra —«35mil», «35 mil»—, y nunca como parte de otra cifra.
   */
  public static boolean estaEscrito(Dinero precio, String texto) {
    if (precio == null || texto == null) {
      return false;
    }
    String compacto = NO_CUENTA.matcher(texto.toLowerCase(Locale.ROOT)).replaceAll("");
    long valor = precio.valor().longValueExact();
    if (Pattern.compile("(?<!\\d)" + valor + "(?!\\d)").matcher(compacto).find()) {
      return true;
    }
    return valor % 1000 == 0
        && Pattern.compile("(?<!\\d)" + valor / 1000 + "mil").matcher(compacto).find();
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
    ProductoExtraido comoSet =
        new ProductoExtraido(
            producto.esProducto(),
            producto.estaAgotado(),
            TituloDeSet.componer(producto.titulo(), prenda.get()),
            producto.linea(),
            tipo.get(),
            costo,
            producto.tallas(),
            producto.cantidadTonos(),
            producto.tonosNombrados(),
            producto.material(),
            textos.map(TextosDelSet::descripcion).orElse(producto.descripcion()),
            textos.map(TextosDelSet::altEn).orElse(producto.altEn()),
            producto.esReplica(),
            producto.confianza(),
            producto.notas(),
            producto.codigoReferencia(),
            producto.tallasPorTono(),
            adicionales,
            producto.marcas(),
            producto.set());
    return Optional.of(
        new SetArmado(comoSet, costo, new ArmadoDeSet(prenda.get(), origen), leidos));
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
