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
      Pattern.compile("\\baumentar?\\s+(\\d|una?|dos|tres|cuatro)\\s+tallas?\\b");

  private static final Pattern PEDIR_MAS =
      Pattern.compile("\\bpedir\\s+(\\d|una?|dos|tres|cuatro)\\s+tallas?\\s+mas\\b");

  /** «36 a la 45 eur», «tallas eur»: la marca EUR va con las tallas, no suelta en el texto. */
  private static final Pattern EUR = Pattern.compile("(\\d{2}|tallas?)\\s*eur\\b");

  private AjusteDelAnuncio() {}

  /**
   * @param texto el mensaje del proveedor
   * @return el ajuste que el texto escribe, o vacío si no escribe ninguno
   */
  public static Optional<AjusteDeTalla> de(String texto) {
    if (texto == null || texto.isBlank()) {
      return Optional.empty();
    }
    String plano =
        DIACRITICOS
            .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT);
    int horma =
        tallasDe(AUMENTAR.matcher(plano)).or(() -> tallasDe(PEDIR_MAS.matcher(plano))).orElse(0);
    int eur = EUR.matcher(plano).find() ? 1 : 0;
    int total = horma + eur;
    if (total == 0 || total > AjusteDeTalla.MAXIMO) {
      return Optional.empty();
    }
    return Optional.of(new AjusteDeTalla(total));
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
