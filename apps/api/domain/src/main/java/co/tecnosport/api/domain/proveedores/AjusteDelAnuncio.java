package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuántas tallas más arriba nombra el proveedor las que anuncia, leído del texto (ADR-0085, 13 de
 * octubre de 2026). Por reglas y no por el modelo: las frases de los proveedores son fijas y
 * cortas, y un ajuste que el texto no escribe es un dato inventado que corre todas las tallas.
 *
 * <ul>
 *   <li>Meraki: «Ojo Horma reducida pedir una talla más» suma 1.
 *   <li>Imperio Wicho: «Aumentar 2 tallas» suma 2; «Aumentar 1 talla», 1.
 *   <li>«Tallas disponibles 36 a la 45 eur» suma 1: la nacional es la EUR menos una.
 *   <li>«Horma normal» no suma nada.
 * </ul>
 *
 * <p>«Horma normal Dama, Aumenta 1 Cab» no es de aquí: el ajuste es solo para caballero, y el
 * anuncio se parte en dos borradores. Lo exige la palabra «talla» después del número, que esa frase
 * no lleva.
 */
public final class AjusteDelAnuncio {

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
  private static final Map<String, Integer> EN_LETRA =
      Map.of("una", 1, "un", 1, "dos", 2, "tres", 3, "cuatro", 4);

  /** «aumentar 2 tallas», «aumenta una talla», «pedir una talla mas», «pedir 2 tallas mas». */
  private static final Pattern AUMENTAR =
      Pattern.compile(
          "\\baumentar?\\s+(\\d|una?|dos|tres|cuatro)\\s+tallas?\\b",
          Pattern.UNICODE_CHARACTER_CLASS);

  private static final Pattern PEDIR_MAS =
      Pattern.compile(
          "\\bpedir\\s+(\\d|una?|dos|tres|cuatro)\\s+tallas?\\s+mas\\b",
          Pattern.UNICODE_CHARACTER_CLASS);

  /**
   * «Horma normal Dama, Aumenta 1 Cab», «Horma normal Dama, aumentar 1 Cab» (Imperio Wicho, 10 de
   * octubre de 2026): el mismo par sirve a dama en su talla y a caballero una más arriba.
   */
  private static final Pattern NORMAL_DAMA_AUMENTA_CABALLERO =
      Pattern.compile(
          "\\bhorma\\s+normal\\s+(?:para\\s+)?damas?\\W+(?:y\\s+)?aumentar?\\s+(\\d|una?|dos|tres|cuatro)"
              + "\\s*(?:tallas?\\s+)?(?:para\\s+)?cab(?:alleros?)?\\b",
          Pattern.UNICODE_CHARACTER_CLASS);

  /**
   * «36 a la 45 eur», «tallas eur»: la marca EUR va con las tallas, no suelta en el texto. Delante
   * también, y abreviada: «Tallas hombre Eu 40 41 42 43 44» de Tenis Al Pie (23 de octubre de
   * 2026). Y detrás de la exclamación con que Imperio Wicho cierra el rango: «36 a la 43! Eur» (27
   * de octubre de 2026).
   */
  private static final Pattern EUR =
      Pattern.compile(
          "(\\d{2}|tallas?)[\\s!.,]*eur\\b|\\beur?\\s*\\d{2}", Pattern.UNICODE_CHARACTER_CLASS);

  private AjusteDelAnuncio() {}

  /**
   * @param texto el mensaje del proveedor
   * @return el ajuste que el texto escribe, o vacío si no escribe ninguno
   */
  public static Optional<AjusteDeTalla> de(String texto) {
    if (texto == null || texto.isBlank()) {
      return Optional.empty();
    }
    String plano = plano(texto);
    int horma =
        tallasDe(AUMENTAR.matcher(plano)).or(() -> tallasDe(PEDIR_MAS.matcher(plano))).orElse(0);
    int eur = EUR.matcher(plano).find() ? 1 : 0;
    int total = horma + eur;
    if (total == 0 || total > AjusteDeTalla.MAXIMO) {
      return Optional.empty();
    }
    return Optional.of(new AjusteDeTalla(total));
  }

  /**
   * El ajuste de cada género cuando el anuncio unisex los da distintos: «Horma normal Dama, Aumenta
   * 1 Cab» es dama igual y caballero uno más arriba. El anuncio se parte en un borrador por género
   * (ADR-0085), y cada uno corre sus tallas con el suyo.
   *
   * @return vacío si el anuncio no los distingue
   */
  public static Optional<PorGenero> porGenero(String texto) {
    if (texto == null || texto.isBlank()) {
      return Optional.empty();
    }
    return tallasDe(NORMAL_DAMA_AUMENTA_CABALLERO.matcher(plano(texto)))
        .filter(tallas -> tallas > 0 && tallas <= AjusteDeTalla.MAXIMO)
        .map(
            tallas -> {
              // «36 a la 45 eur» también aquí: las dos partes nacen de tallas EUR.
              int eur = EUR.matcher(plano(texto)).find() ? 1 : 0;
              return new PorGenero(
                  new AjusteDeTalla(eur),
                  new AjusteDeTalla(Math.min(tallas + eur, AjusteDeTalla.MAXIMO)));
            });
  }

  /** El de dama y el de caballero de un mismo anuncio. */
  public record PorGenero(AjusteDeTalla dama, AjusteDeTalla caballero) {}

  private static String plano(String texto) {
    return DIACRITICOS
        .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
        .replaceAll("")
        .toLowerCase(Locale.ROOT);
  }

  private static Optional<Integer> tallasDe(Matcher matcher) {
    if (!matcher.find()) {
      return Optional.empty();
    }
    String cantidad = matcher.group(1);
    return Optional.of(
        cantidad.chars().allMatch(Character::isDigit)
            ? Integer.parseInt(cantidad)
            : EN_LETRA.get(cantidad));
  }
}
