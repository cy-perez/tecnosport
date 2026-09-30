package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Un título reducido a lo que lo identifica: minúsculas, sin tildes, sin emojis ni signos, con un
 * solo espacio entre palabras. «Bolso de dama mediano 👜» y «BOLSO DE DAMA MEDIANO» son el mismo
 * producto, y la huella se calcula sobre esto y no sobre lo que escribió el proveedor ese día.
 */
public final class NormalizadorDeTitulo {

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
  private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

  private NormalizadorDeTitulo() {}

  public static String normalizar(String titulo) {
    if (titulo == null) {
      return "";
    }
    String sinTildes =
        DIACRITICOS.matcher(Normalizer.normalize(titulo, Normalizer.Form.NFD)).replaceAll("");
    return NO_ALFANUMERICO.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
  }
}
