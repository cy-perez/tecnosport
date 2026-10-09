package co.tecnosport.api.domain.proveedores;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las prendas se nombran como su categoría, no con el diminutivo del proveedor: «Busito manga
 * larga» es «Buzo manga larga», en el título, en la descripción y en todo lo que se publica
 * (decidido por el negocio el 9 de octubre de 2026, con la exportación de La Riverah). La categoría
 * del catálogo se llama «Buzos», así que «buso», como lo escribe Meraki, también se corrige.
 *
 * <p>La lista es explícita a propósito: un sufijo «-ito» suelto convertiría «bonito» en «bon». Solo
 * entran los diminutivos de las prendas que se venden; uno nuevo se agrega con su ejemplo. El
 * plural se conserva, y la mayúscula también: la de la inicial, o la palabra entera si venía toda
 * en mayúsculas.
 */
public final class NombreDeCategoria {

  private static final Map<String, String> CORRECCIONES = new LinkedHashMap<>();

  static {
    singularYPlural("buzo", "buzos", "busito", "buzito", "buso");
    singularYPlural("camiseta", "camisetas", "camisetica", "camisetita");
    singularYPlural("blusa", "blusas", "blusita", "blusica");
    singularYPlural("chaqueta", "chaquetas", "chaquetica", "chaquetita");
    singularYPlural("falda", "faldas", "faldita", "faldica");
    singularYPlural("vestido", "vestidos", "vestidito", "vestidico");
    singularYPlural("short", "shorts", "shortcito", "shorcito");
    singularYPlural("pantalón", "pantalones", "pantaloncito");
    singularYPlural("bolso", "bolsos", "bolsito", "bolsico");
    singularYPlural("morral", "morrales", "morralito", "morralcito");
    singularYPlural("conjunto", "conjuntos", "conjuntico", "conjuntito");
    singularYPlural("bodi", "bodis", "bodicito");
  }

  private static final Pattern PALABRA =
      Pattern.compile(
          "\\b(" + String.join("|", CORRECCIONES.keySet()) + ")\\b",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

  private NombreDeCategoria() {}

  /**
   * @return el texto con cada diminutivo de la lista cambiado por el nombre de su categoría, o nulo
   *     si llegó nulo
   */
  public static String corregir(String texto) {
    if (texto == null) {
      return null;
    }
    Matcher m = PALABRA.matcher(texto);
    StringBuilder corregido = new StringBuilder();
    while (m.find()) {
      String original = m.group(1);
      String base = CORRECCIONES.get(original.toLowerCase(Locale.ROOT));
      m.appendReplacement(corregido, Matcher.quoteReplacement(conMayusculasDe(original, base)));
    }
    m.appendTail(corregido);
    return corregido.toString();
  }

  private static String conMayusculasDe(String original, String base) {
    if (original.length() > 1 && original.equals(original.toUpperCase(Locale.ROOT))) {
      return base.toUpperCase(Locale.ROOT);
    }
    if (Character.isUpperCase(original.charAt(0))) {
      return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }
    return base;
  }

  /** Cada diminutivo en singular va al singular, y su plural en -s al plural. */
  private static void singularYPlural(String singular, String plural, String... diminutivos) {
    for (String diminutivo : diminutivos) {
      CORRECCIONES.put(diminutivo, singular);
      CORRECCIONES.put(diminutivo + "s", plural);
    }
  }
}
