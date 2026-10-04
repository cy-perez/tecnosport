package co.tecnosport.api.domain.proveedores;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un rango de tallas numéricas en el texto —«Tallas 30 a la 36», «talla 28-34», «tallas del 6 al
 * 14»— contado de 2 en 2: 30, 32, 34, 36. Es como tallan los jeans y los pantalones en Colombia
 * (decidido por el negocio el 4 de octubre de 2026), y el modelo a veces devolvía solo los dos
 * extremos o los contaba de 1 en 1.
 *
 * <p>Solo para pantalones y shorts: en calzado «34 al 40» sí va de 1 en 1, y eso lo decide quien
 * llama según el tipo. Se abstiene —devuelve vacío— cuando no hay que adivinar: sin la palabra
 * «talla» delante, con dos rangos en el mismo mensaje (¿de cuál producto es cada uno?), con los
 * extremos al revés o de distinta paridad («30 a 35» no se cuenta de 2 en 2).
 */
public final class RangoDeTallas {

  private static final Pattern RANGO =
      Pattern.compile(
          "\\btallas?\\s*:?\\s*(?:de(?:l|sde)?\\s+(?:la\\s+)?)?(\\d{1,2})\\s*"
              + "(?:a\\s+la|al|a|hasta\\s+la|hasta|-|–|—)\\s*(\\d{1,2})\\b");
  private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

  /** Un rango más ancho que esto no es una escala de tallas sino otra cosa. */
  private static final int AMPLITUD_MAXIMA = 20;

  private RangoDeTallas() {}

  /**
   * @return las tallas del rango de 2 en 2, o vacío si el texto no trae exactamente un rango que se
   *     pueda contar así
   */
  public static Optional<List<String>> deDosEnDos(String texto) {
    if (texto == null) {
      return Optional.empty();
    }
    String plano =
        DIACRITICOS
            .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[*_~]", " ");
    Matcher m = RANGO.matcher(plano);
    if (!m.find()) {
      return Optional.empty();
    }
    int desde = Integer.parseInt(m.group(1));
    int hasta = Integer.parseInt(m.group(2));
    if (m.find()) {
      return Optional.empty();
    }
    if (hasta <= desde || hasta - desde > AMPLITUD_MAXIMA || (hasta - desde) % 2 != 0) {
      return Optional.empty();
    }
    List<String> tallas = new ArrayList<>();
    for (int talla = desde; talla <= hasta; talla += 2) {
      tallas.add(String.valueOf(talla));
    }
    return Optional.of(List.copyOf(tallas));
  }
}
