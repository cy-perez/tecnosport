package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * El texto con que el proveedor publicó un anuncio, reducido a lo que dice: sin mayúsculas, sin
 * tildes, sin emojis, sin asteriscos y sin la cuenta de espacios. Dos anuncios con el mismo texto
 * normalizado son el mismo anuncio, aunque uno venga de Android y otro de iPhone.
 *
 * <p>Existe porque el título no sirve para eso: es del extractor, y el modelo no titula igual dos
 * veces. Los dos tenis de Imperio Wicho a 65.000 del 8 de octubre de 2026 —mismo texto letra por
 * letra, misma foto— salieron como «Tenis estilo Cab importado» y «Tenis importado tipo media
 * ultraliviano», y ninguno se reconoció como el repetido del otro. El texto del proveedor no cambia
 * entre una lectura y otra.
 */
public final class TextoDeAnuncio {

  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
  private static final Pattern NO_LETRA_NI_CIFRA = Pattern.compile("[^\\p{L}\\p{N}]+");

  private TextoDeAnuncio() {}

  /**
   * @return el texto normalizado; vacío si llegó nulo o no tenía ni una letra ni una cifra
   */
  public static String normalizar(String texto) {
    if (texto == null) {
      return "";
    }
    String sinTildes =
        DIACRITICOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
    return NO_LETRA_NI_CIFRA.matcher(sinTildes.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
  }

  /** Si los dos textos dicen lo mismo. Dos textos vacíos no son el mismo anuncio: no dicen nada. */
  public static boolean mismoAnuncio(String uno, String otro) {
    String a = normalizar(uno);
    return !a.isEmpty() && a.equals(normalizar(otro));
  }
}
