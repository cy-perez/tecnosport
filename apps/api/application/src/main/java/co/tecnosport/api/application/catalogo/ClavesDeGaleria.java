package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.util.UUID;

/**
 * Dónde vive cada imagen de galería dentro del bucket, con el mismo criterio que {@link
 * ClavesDeRotacion}: la key la arma siempre el servidor y el cliente solo recibe la URL firmada que
 * apunta a ella.
 *
 * <p>Existe como clase y no como dos literales sueltos porque el prefijo lo escribe {@link
 * SolicitarSubidaDeImagenDeGaleria} y lo <b>exige</b> {@link AgregarImagenDeGaleria}, y esas dos
 * cosas tienen que ser la misma cadena. Cuando no lo eran —la confirmación se conformaba con {@code
 * productos/{id}/}— se podía confirmar una key de {@code principal-} como imagen de galería, y el
 * siguiente reemplazo de la imagen principal borraba el objeto que la galería estaba sirviendo.
 */
final class ClavesDeGaleria {

  private ClavesDeGaleria() {}

  static String prefijoDe(UUID productoId) {
    return "productos/" + productoId + "/galeria-";
  }

  static String nueva(UUID productoId, String extension) {
    return prefijoDe(productoId) + GeneradorIdentificador.nuevo() + "." + extension;
  }
}
