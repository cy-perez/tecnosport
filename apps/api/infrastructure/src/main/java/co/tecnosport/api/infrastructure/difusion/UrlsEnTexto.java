package co.tecnosport.api.infrastructure.difusion;

import java.util.Arrays;
import java.util.List;

/**
 * Las fotos de una publicación guardadas como texto, una URL por línea (V85).
 *
 * <p>Misma forma que {@code EscalaDeTallasEnTexto} y por la misma razón: la lista se lee entera con
 * su fila y nadie busca nunca una publicación por una de sus fotos, así que una tabla de unión solo
 * añadiría un join. Una URL no lleva saltos de línea, así que el separador no puede chocar con el
 * contenido.
 */
final class UrlsEnTexto {

  private static final String SEPARADOR = "\n";

  private UrlsEnTexto() {}

  static String unir(List<String> urls) {
    return String.join(SEPARADOR, urls);
  }

  static List<String> partir(String texto) {
    if (texto == null || texto.isBlank()) {
      return List.of();
    }
    return Arrays.stream(texto.split(SEPARADOR))
        .map(String::strip)
        .filter(url -> !url.isEmpty())
        .toList();
  }
}
