package co.tecnosport.api.application.catalogo;

import java.util.Optional;

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
   * Borra todos los objetos cuya key empiece por el prefijo dado, y devuelve cuántos borró.
   *
   * <p>Por prefijo y no objeto por objeto a propósito: un set que murió a medio subir dejó objetos
   * en el bucket que nunca llegaron a ser una fila en la base de datos. Recorrer los fotogramas
   * conocidos dejaría esos justamente afuera, que son los que más falta hace reclamar.
   *
   * <p>Es idempotente: borrar un prefijo que ya no tiene nada devuelve cero, no falla.
   */
  int eliminarPorPrefijo(String prefijo);
}
