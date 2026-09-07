package co.tecnosport.api.application.catalogo;

import java.util.Optional;
import java.util.Set;

/**
 * Puerto de almacenamiento de imágenes. Implementación de producción: Cloud Storage con URL firmada
 * — las imágenes se suben directo desde el navegador, nunca pasan por el backend
 * (docs/07-infra-gcp.md).
 */
public interface AlmacenDeImagenes {

  UrlFirmada generarUrlDeSubida(String objectKey, String contentType);

  /** Vacío si el objeto no existe todavía — quien llama lo interpreta como "no se subió". */
  Optional<Long> tamanoBytes(String objectKey);

  String urlPublica(String objectKey);

  /**
   * Borra los objetos cuya key empiece por el prefijo dado, salvo los de {@code conservar}, y
   * devuelve cuántos borró.
   *
   * <p>Por prefijo y no objeto por objeto a propósito: una subida que murió a mitad dejó objetos en
   * el bucket que nunca llegaron a ser una fila en la base de datos. Recorrer los conocidos dejaría
   * esos justamente afuera, que son los que más falta hace reclamar.
   *
   * <p>{@code conservar} existe porque reemplazar la imagen principal comparte prefijo con la que
   * acaba de subirse: se limpia todo lo viejo bajo {@code productos/{id}/principal-} menos la key
   * nueva. Para borrar un prefijo entero, {@code Set.of()}.
   *
   * <p>Es idempotente: borrar un prefijo que ya no tiene nada devuelve cero, no falla.
   */
  int eliminarPorPrefijo(String prefijo, Set<String> conservar);
}
