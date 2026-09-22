package co.tecnosport.api.application.catalogo;

import java.util.Map;

/**
 * Los tipos de contenido que se aceptan al pedir una URL firmada, y su extensión. Lista blanca
 * declarada por el cliente: nadie verifica que los bytes reales correspondan al tipo declarado
 * (ADR-0016).
 *
 * <p><b>{@code image/avif} entró el 21 de septiembre de 2026</b>, y no por gusto: el cargador del
 * catálogo subía la foto <i>maestra</i> del estudio —un artefacto de archivo de 635 kB— porque era
 * lo único que la lista dejaba pasar, y esa foto era el elemento más pesado de la portada y de la
 * ficha en la primera medición de Lighthouse que valió algo. El AVIF de 1200 px del mismo
 * fotograma pesa 58 kB.
 *
 * <p>Lo que hay que saber antes de apoyarse en esto: <b>este proyecto no declara una matriz de
 * navegadores</b> —no hay `browserslist` ni una línea en `docs/00-producto.md`— y el sitio no
 * envuelve las imágenes en un {@code <picture>} con respaldo. O sea que quien abra la tienda en un
 * Safari anterior al 16 (2022) no verá la foto, no verá una peor. Si algún día eso importa, el
 * respaldo se pone en la plantilla; la lista blanca de aquí no es el sitio donde se decide.
 */
final class TiposDeImagen {

  private static final Map<String, String> EXTENSIONES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "image/avif", "avif");

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
