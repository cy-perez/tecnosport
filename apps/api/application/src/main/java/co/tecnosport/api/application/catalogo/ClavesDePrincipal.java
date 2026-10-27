package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.UUID;

/**
 * Dónde vive la imagen principal dentro del bucket, con el mismo criterio que {@link
 * ClavesDeGaleria}: la key la arma siempre el servidor y el cliente solo recibe la URL firmada que
 * apunta a ella.
 *
 * <p>Existe por lo mismo que existe {@code ClavesDeGaleria}, y llega tarde: el prefijo se escribía
 * <b>a mano en tres sitios</b> —al firmar la subida, al validar la confirmación y al limpiar— y la
 * validación se conformaba con {@code productos/{id}/}. El javadoc de {@code ClavesDeGaleria}
 * describe el daño palabra por palabra, porque es el mismo defecto que ya se arregló del otro lado
 * y no se portó a este: con el prefijo corto, confirmar como imagen principal una key de {@code
 * galeria-} del mismo producto pasaba las tres guardas, y entonces la limpieza —que borra el
 * prefijo {@code principal-} entero salvo las claves recién confirmadas, y esas son de galería— se
 * llevaba por delante los objetos {@code principal-} de verdad. La principal quedaba apuntando a un
 * objeto de galería vivo, y el siguiente {@code DELETE} de esa imagen de galería dejaba la foto
 * principal rota en una ficha publicada.
 *
 * <p>Es además la regla dura #7: la key la manda el cliente, y comprobar solo que empiece por el id
 * del producto es confiar en él para decidir qué objeto se borra.
 */
public final class ClavesDePrincipal {

  private ClavesDePrincipal() {}

  static String prefijoDe(UUID productoId) {
    return "productos/" + productoId + "/principal-";
  }

  /**
   * Pública porque la usan también las aprobaciones de los borradores de proveedor, que copian
   * fotos al bucket con estas mismas claves: si las escribieran a mano, la limpieza de la principal
   * y el borrado de la galería dejarían de reconocerlas.
   */
  public static String nueva(UUID productoId, String extension) {
    return prefijoDe(productoId) + GeneradorIdentificador.nuevo() + "." + extension;
  }
}
