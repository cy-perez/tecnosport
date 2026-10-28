package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El acompañante y el combo que el anuncio escribe con su precio en la misma línea: «Gorra 🧢
 * $35.000», «Básica 48.000», «Nota: Las gorras tiene un valor de 35mil-», «Polo+ gorra $ 85.000»,
 * «Dúo $95.000».
 *
 * <p>Existe porque el modelo no siempre los devuelve. Los dos anuncios de camisetas oversize de La
 * Riverah del 9 de octubre de 2026 traen la nota de las gorras, que el prompt cita palabra por
 * palabra como un acompañante, y en la ingesta del 12 de octubre ninguno de los dos salió con ella:
 * los dos borradores quedaron como la camiseta sola a 55.000, cuando el set cuesta 90.000.
 * Pedírselo mejor al modelo ya no servía; leerlo del texto sí.
 *
 * <p>Una línea que nombra un set, un dúo o un combo, o que suma dos prendas —«Polo+ gorra», «falda
 * + básica»—, es un combo: su precio es el del set, y {@link ArmadorDeSets} lo prefiere a la suma.
 * Si la línea no dice la prenda —«Dúo $95.000»—, es la del único acompañante que el texto escribe.
 * Sin leer el combo, el set del polo saldría a 90.000 (55.000 más la gorra) cuando el anuncio dice
 * 85.000.
 *
 * <p>Lo tachado no se lee, como en {@link PatronDePrecio}: «Jean + Básica 🤑~$75.000~» y debajo
 * «Descuento aniversario $68.000» (La Riverah, 26 de octubre de 2026) era un combo a 75.000, y el
 * combo manda en el set.
 */
public final class AcompananteEscrito {

  private static final Pattern GORRA =
      Pattern.compile(
          "(?<!\\p{L})(?:gorras?|cachuchas?)(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern BASICA =
      Pattern.compile(
          "(?<!\\p{L})b[aá]sicas?(?!\\p{L})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Lo que hace de la línea un combo: el set, el dúo, el combo, o dos prendas sumadas. */
  private static final Pattern COMBO =
      Pattern.compile(
          "(?<!\\p{L})(?:sets?|d[uú]os?|combos?)(?!\\p{L})|\\+|/",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** «35mil», «35 mil». */
  private static final Pattern EN_MILES =
      Pattern.compile(
          "(?<![\\d.,])(\\d{1,3})\\s*mil(?!\\p{L})",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** «$35.000», «35.000», «35,000», «35000». Desde mil: menos no es el precio de una prenda. */
  private static final Pattern COMPLETO =
      Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,7})(?![\\d.,]?\\d)");

  private AcompananteEscrito() {}

  /**
   * @return los acompañantes y los combos escritos con precio, en el orden del texto: nunca dos de
   *     la misma clase y la misma prenda
   */
  public static List<PrecioAdicional> en(String texto) {
    if (texto == null) {
      return List.of();
    }
    List<PrecioAdicional> leidos = new ArrayList<>();
    List<Dinero> combosSinPrenda = new ArrayList<>();
    for (String linea : PatronDePrecio.sinTachado(texto).split("\\R")) {
      if (esTelefono(linea)) {
        continue;
      }
      Optional<PrendaAcompanante> prenda = prendaDe(linea);
      boolean esCombo = COMBO.matcher(linea).find();
      if (esCombo) {
        Optional<Dinero> precio = precioDespuesDe(linea, 0);
        if (precio.isEmpty()) {
          continue;
        }
        if (prenda.isEmpty()) {
          combosSinPrenda.add(precio.get());
        } else {
          agregar(leidos, "Combo", precio.get(), ClaseDePrecioAdicional.COMBO, prenda.get());
        }
        continue;
      }
      if (prenda.isEmpty()) {
        continue;
      }
      Matcher nombre = (prenda.get() == PrendaAcompanante.GORRA ? GORRA : BASICA).matcher(linea);
      nombre.find();
      precioDespuesDe(linea, nombre.end())
          .ifPresent(
              precio ->
                  agregar(
                      leidos,
                      prenda.get() == PrendaAcompanante.GORRA ? "Gorra" : "Básica",
                      precio,
                      ClaseDePrecioAdicional.ACOMPANANTE,
                      prenda.get()));
    }
    // «Dúo $95.000» no dice la prenda: es la del único acompañante que el texto escribe.
    List<PrendaAcompanante> acompanantes =
        leidos.stream()
            .filter(p -> p.clase() == ClaseDePrecioAdicional.ACOMPANANTE)
            .map(PrecioAdicional::prenda)
            .distinct()
            .toList();
    if (acompanantes.size() == 1) {
      combosSinPrenda.forEach(
          precio ->
              agregar(
                  leidos, "Combo", precio, ClaseDePrecioAdicional.COMBO, acompanantes.getFirst()));
    }
    return List.copyOf(leidos);
  }

  private static void agregar(
      List<PrecioAdicional> leidos,
      String concepto,
      Dinero precio,
      ClaseDePrecioAdicional clase,
      PrendaAcompanante prenda) {
    if (leidos.stream().noneMatch(p -> p.clase() == clase && p.prenda() == prenda)) {
      leidos.add(new PrecioAdicional(concepto, precio, clase, prenda));
    }
  }

  /**
   * «📦*+57 301 9854097*»: la línea del pedido lleva un «+» y cifras que no son un precio. Ningún
   * precio de una prenda junta diez cifras en una línea.
   */
  private static boolean esTelefono(String linea) {
    return linea.chars().filter(Character::isDigit).count() >= 10;
  }

  private static Optional<PrendaAcompanante> prendaDe(String linea) {
    if (GORRA.matcher(linea).find()) {
      return Optional.of(PrendaAcompanante.GORRA);
    }
    if (BASICA.matcher(linea).find()) {
      return Optional.of(PrendaAcompanante.BLUSA);
    }
    return Optional.empty();
  }

  /** El primer precio que la línea escribe desde esa posición. */
  private static Optional<Dinero> precioDespuesDe(String linea, int desde) {
    String resto = linea.substring(desde);
    Matcher miles = EN_MILES.matcher(resto);
    Matcher completo = COMPLETO.matcher(resto);
    boolean hayMiles = miles.find();
    boolean hayCompleto = completo.find();
    if (!hayMiles && !hayCompleto) {
      return Optional.empty();
    }
    if (hayMiles && (!hayCompleto || miles.start() < completo.start())) {
      return Optional.of(
          Dinero.deCop(new BigDecimal(miles.group(1)).multiply(BigDecimal.valueOf(1000))));
    }
    return Optional.of(Dinero.deCop(new BigDecimal(completo.group(1).replaceAll("[.,]", ""))));
  }
}
