package co.tecnosport.api.domain.catalogo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que alguien busca con una palabra y el catálogo nombra con otra.
 *
 * <p>El catálogo escribe «bodi» —«bodis» en plural—, que es como se escribe en español (3 de
 * octubre de 2026), pero quien busca escribe «body» casi siempre. La búsqueda compara por semejanza
 * de trigramas y «body» queda lejos de «bodi herraje», así que la palabra se cambia antes de
 * buscar. Si aparecen más pares así, van aquí.
 */
public final class SinonimosDeBusqueda {

  private static final Pattern BODY =
      Pattern.compile("\\bbod(?:y|ie)(s)?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private SinonimosDeBusqueda() {}

  /**
   * @return el texto con los sinónimos llevados a como los escribe el catálogo, o nulo si llegó
   *     nulo
   */
  public static String aplicar(String texto) {
    if (texto == null) {
      return null;
    }
    Matcher m = BODY.matcher(texto);
    StringBuilder resultado = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(resultado, m.group(1) == null ? "bodi" : "bodis");
    }
    m.appendTail(resultado);
    return resultado.toString();
  }
}
