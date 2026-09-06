package co.tecnosport.api.application.catalogo;

import java.util.Map;

/**
 * Los tipos de contenido que se aceptan al pedir una URL firmada, y su extensión. Lista blanca
 * declarada por el cliente: nadie verifica que los bytes reales correspondan al tipo declarado
 * (ADR-0016).
 */
final class TiposDeImagen {

  private static final Map<String, String> EXTENSIONES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private TiposDeImagen() {}

  static String extensionDe(String contentType) {
    String extension = EXTENSIONES.get(contentType);
    if (extension == null) {
      throw new IllegalArgumentException(
          "Tipo de contenido de imagen no soportado: " + contentType);
    }
    return extension;
  }
}
