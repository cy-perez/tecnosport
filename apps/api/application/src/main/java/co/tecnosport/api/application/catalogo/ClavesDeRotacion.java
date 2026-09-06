package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.SetRotacion;

/**
 * Dónde vive cada fotograma dentro del bucket. La arma siempre el servidor: el cliente nunca elige
 * la key, solo recibe la URL firmada que apunta a ella.
 */
final class ClavesDeRotacion {

  private ClavesDeRotacion() {}

  static String prefijoDe(SetRotacion set) {
    return "productos/" + set.productoId() + "/rotacion/" + set.id() + "/";
  }

  static String deFotograma(SetRotacion set, int orden, String extension) {
    return prefijoDe(set) + orden + "." + extension;
  }
}
