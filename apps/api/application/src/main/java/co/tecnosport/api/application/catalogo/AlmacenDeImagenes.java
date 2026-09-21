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
   * El camino de vuelta de {@link #urlPublica(String)}: qué objeto hay detrás de una URL ya
   * guardada. Hace falta para borrar la imagen de una galería, que es lo único que se elimina
   * conociendo solo la URL —la principal se limpia por prefijo, y un set de rotación borra el suyo
   * entero—.
   *
   * <p>Vacío cuando la URL no es de este almacén, y ese caso <b>existe de verdad</b>: el catálogo
   * sembrado de {@code local} y {@code dev} trae imágenes de picsum.photos. Quitar una de esas
   * tiene que sacar la fila y no intentar borrar nada, no reventar.
   */
  Optional<String> objectKeyDe(String urlPublica);

  /**
   * Borra un objeto concreto y dice si había algo que borrar.
   *
   * <p>Existe además de {@link #eliminarPorPrefijo} porque las imágenes de una galería comparten
   * prefijo y siguen vivas: limpiar {@code productos/{id}/galeria-} para quitar una se llevaría las
   * hermanas. Pasar la key entera como prefijo funcionaría hoy por la forma de las keys, y esa es
   * exactamente la clase de casualidad que deja de ser cierta sin que nadie se entere.
   *
   * <p>Idempotente: borrar algo que ya no está devuelve {@code false}, no falla.
   */
  boolean eliminar(String objectKey);

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
