package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import java.util.Optional;

/**
 * El bucket privado de los originales del proveedor: las exportaciones subidas y las fotos tal como
 * llegaron.
 *
 * <p>Es un puerto aparte de {@code AlmacenDeImagenes} porque al otro lado hay otra cosa: un bucket
 * que nadie lee sin firma, donde el servidor sí escribe bytes. El de imágenes es público de lectura
 * y el servidor nunca le escribe —firma y borra—. Compartirlos habría metido {@code guardar} en un
 * puerto cuya mitad de implementaciones no debe guardar nada.
 */
public interface AlmacenDeArchivosDeProveedor {

  /** Una URL firmada de {@code PUT} para que el panel suba la exportación sin pasar por aquí. */
  UrlFirmada generarUrlDeSubida(String objectKey, String contentType);

  /** Vacío si el objeto no existe. */
  Optional<Long> tamanoBytes(String objectKey);

  void guardar(String objectKey, String contentType, byte[] bytes);

  Optional<byte[]> leer(String objectKey);

  /** Una URL firmada de {@code GET}, de vida corta, para que el panel muestre una foto. */
  UrlFirmada urlDeLectura(String objectKey);

  /** Borrar lo que ya no está no falla: reintentar un borrado a medias tiene que poder terminar. */
  void borrar(String objectKey);

  /**
   * Borra todo lo que cuelga de este prefijo, lo nombre una fila o no: lo que se subió y nunca se
   * registró —una subida firmada que el panel no confirmó— también es del borrador (27 de octubre
   * de 2026). Como {@link #borrar}, repetirlo no falla.
   *
   * @param prefijo termina en {@code /}, para no llevarse lo de un vecino cuyo id empiece igual
   * @return cuántos objetos borró
   */
  int borrarPorPrefijo(String prefijo);
}
