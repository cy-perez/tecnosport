package co.tecnosport.api.domain.proveedores;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * El título de cada borrador de un anuncio unisex que se parte por género (ADR-0085): «Tenis estilo
 * Nike unisex» es «Tenis estilo Nike para dama» y «Tenis estilo Nike para caballero». Dice el
 * género porque quien busca lo busca así, y porque los dos productos tienen que poder distinguirse
 * en el panel.
 */
public final class TituloPorGenero {

  private static final Pattern GENERO =
      Pattern.compile(
          "\\s*\\b(?:unisex|(?:para|de)\\s+(?:damas?|caballeros?|hombres?|mujer(?:es)?)(?:\\s+(?:y|e)\\s+(?:damas?|caballeros?|hombres?|mujer(?:es)?))?)\\b",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private TituloPorGenero() {}

  /**
   * @param genero «dama» o «caballero»
   * @return el título sin «unisex» ni otro género, con «para» y el suyo al final; nulo si no hay
   *     título
   */
  public static String para(String titulo, String genero) {
    if (titulo == null || titulo.isBlank()) {
      return null;
    }
    String sinGenero = GENERO.matcher(titulo).replaceAll("").replaceAll("\\s{2,}", " ").strip();
    return sinGenero + " para " + genero.toLowerCase(Locale.ROOT);
  }
}
