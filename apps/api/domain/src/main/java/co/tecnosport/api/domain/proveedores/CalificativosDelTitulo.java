package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las palabras del título que describen la prenda —«plus», «oversize», «licrado»— solo si el
 * anuncio las escribe.
 *
 * <p>El 12 de octubre de 2026 «*Jeans AA para caballero* ⭕️Marca Levis» salió titulado «Jean plus
 * para caballero importado»: el prompt traía de ejemplo «Jeans AA plus para caballero», que es otro
 * anuncio del mismo proveedor, y el modelo copió el ejemplo. Una talla plus que no existe en el
 * anuncio es una promesa falsa en la ficha. Es una lista cerrada a propósito: lo que no está en
 * ella —«para caballero», «manga larga»— el modelo lo puede redactar con sus palabras.
 */
public final class CalificativosDelTitulo {

  /**
   * Por la raíz, no por la palabra: «Jean licrado» con «Tela Licrada» en el anuncio dice lo que el
   * anuncio dice, y lo mismo «oversize» con «OVERSIZED».
   */
  private static final List<String> CALIFICATIVOS =
      List.of(
          "plus", "oversized?", "slim", "skinny", "baggy", "cargo", "licrad[oa]", "semilicrad[oa]");

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

  private CalificativosDelTitulo() {}

  /** El título sin los calificativos que el texto no escribe. Nulo si llegó nulo. */
  public static String sinLosQueElTextoNoEscribe(String titulo, String texto) {
    if (titulo == null || texto == null) {
      return titulo;
    }
    String delTexto = normalizado(texto);
    String resultado = titulo;
    for (String calificativo : CALIFICATIVOS) {
      Pattern palabra =
          Pattern.compile(
              "(?<!\\p{L})" + calificativo + "s?(?!\\p{L})",
              Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      if (palabra.matcher(delTexto).find()) {
        continue;
      }
      Matcher m = palabra.matcher(resultado);
      if (m.find()) {
        resultado = m.replaceAll(" ").replaceAll("\\s+", " ").strip();
      }
    }
    return resultado.isEmpty() ? titulo : resultado;
  }

  /** En minúsculas y sin tildes: «LICRADA» y «licráda» son «licrada». */
  static String normalizado(String texto) {
    return DIACRITICOS
        .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
        .replaceAll("")
        .toLowerCase(Locale.ROOT);
  }
}
