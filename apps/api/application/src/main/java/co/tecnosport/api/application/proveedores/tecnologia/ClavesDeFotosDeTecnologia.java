package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dónde viven las fotos de un borrador de tecnología en el bucket privado del proveedor, y qué
 * tipos se admiten.
 *
 * <p>El bucket privado y no el público, como las fotos que se suben a un borrador de prendas: hasta
 * que alguien apruebe no son de ningún producto, y el informe de huérfanos del bucket público no
 * tiene por qué verlas. Aprobar las copia al público con las claves del producto.
 *
 * <p>Los tipos son los que deja el estudio de fotos: AVIF para los anchos web y JPEG para la vista
 * previa. PNG y WebP entran porque son los otros dos que el sitio sirve.
 */
final class ClavesDeFotosDeTecnologia {

  private static final Map<String, String> EXTENSIONES =
      Map.of(
          "image/avif", "avif",
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  private ClavesDeFotosDeTecnologia() {}

  static String nueva(UUID proveedorId, UUID borradorId, String contentType) {
    String extension = EXTENSIONES.get(contentType);
    if (extension == null) {
      throw new IllegalArgumentException(
          "Una foto de un borrador de tecnología es AVIF, JPEG, PNG o WebP, no "
              + contentType
              + ".");
    }
    return prefijo(proveedorId, borradorId) + GeneradorIdentificador.nuevo() + "." + extension;
  }

  static boolean esDelBorrador(UUID proveedorId, UUID borradorId, String objectKey) {
    return objectKey != null
        && objectKey.startsWith(prefijo(proveedorId, borradorId))
        && !objectKey.contains("..")
        && EXTENSIONES.values().stream().anyMatch(e -> objectKey.endsWith("." + e));
  }

  /** El tipo de contenido que corresponde a la extensión de una clave ya validada. */
  static String contentTypeDe(String objectKey) {
    String minuscula = objectKey.toLowerCase(Locale.ROOT);
    return EXTENSIONES.entrySet().stream()
        .filter(e -> minuscula.endsWith("." + e.getValue()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Extensión desconocida: " + objectKey));
  }

  /** La extensión de una clave ya validada. */
  static String extensionDe(String objectKey) {
    return EXTENSIONES.get(contentTypeDe(objectKey));
  }

  private static String prefijo(UUID proveedorId, UUID borradorId) {
    return "proveedores/" + proveedorId + "/borradores-tecnologia/" + borradorId + "/";
  }
}
