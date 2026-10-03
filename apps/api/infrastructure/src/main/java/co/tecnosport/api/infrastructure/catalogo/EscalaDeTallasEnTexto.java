package co.tecnosport.api.infrastructure.catalogo;

import java.util.Arrays;
import java.util.List;

/** La escala de tallas guardada como texto, una talla por línea (V75). */
final class EscalaDeTallasEnTexto {

  private static final String SEPARADOR = "\n";

  private EscalaDeTallasEnTexto() {}

  static String unir(List<String> tallas) {
    return tallas == null || tallas.isEmpty() ? null : String.join(SEPARADOR, tallas);
  }

  static List<String> partir(String texto) {
    if (texto == null || texto.isBlank()) {
      return List.of();
    }
    return Arrays.stream(texto.split(SEPARADOR))
        .map(String::strip)
        .filter(t -> !t.isEmpty())
        .toList();
  }
}
