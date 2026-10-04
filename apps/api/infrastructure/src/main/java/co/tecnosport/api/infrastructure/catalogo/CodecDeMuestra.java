package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.domain.catalogo.MuestraDeColor;
import co.tecnosport.api.domain.catalogo.ParteDeMuestra;
import co.tecnosport.api.domain.catalogo.PatronDeColor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * La muestra de un color como la guarda {@code variante_atributo_valor.muestra} (V80): las
 * porciones separadas por punto y coma, cada una un HEX o el patrón con sus colores —{@code
 * "#111111;#C62828"}, {@code "ANIMAL_PRINT:#C19A6B,#3B2A1A;#111111"}—.
 */
final class CodecDeMuestra {

  private static final String ENTRE_PARTES = ";";
  private static final String TRAS_PATRON = ":";
  private static final String ENTRE_COLORES = ",";

  private CodecDeMuestra() {}

  static String aTexto(MuestraDeColor muestra) {
    if (muestra == null) {
      return null;
    }
    return muestra.partes().stream()
        .map(
            parte ->
                parte.esLisa()
                    ? parte.colores().get(0)
                    : parte.patron().name()
                        + TRAS_PATRON
                        + String.join(ENTRE_COLORES, parte.colores()))
        .collect(Collectors.joining(ENTRE_PARTES));
  }

  /**
   * @param colorHex lo que hay en {@code color_hex}: la muestra de una fila de antes de V80, que no
   *     guardaba otra cosa
   */
  static MuestraDeColor deTexto(String texto, String colorHex) {
    if (texto == null || texto.isBlank()) {
      return colorHex == null ? null : MuestraDeColor.lisa(colorHex);
    }
    List<ParteDeMuestra> partes =
        Arrays.stream(texto.split(ENTRE_PARTES)).map(CodecDeMuestra::parte).toList();
    return new MuestraDeColor(partes);
  }

  private static ParteDeMuestra parte(String texto) {
    int separador = texto.indexOf(TRAS_PATRON);
    if (separador < 0) {
      return ParteDeMuestra.lisa(texto);
    }
    return new ParteDeMuestra(
        PatronDeColor.valueOf(texto.substring(0, separador)),
        List.of(texto.substring(separador + 1).split(ENTRE_COLORES)));
  }
}
