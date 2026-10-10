package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los productos en {@code BORRADOR}, vistos solo como ids, para el borrado en bloque. Un puerto
 * aparte de {@link RepositorioProductos} porque es una lectura de una sola pantalla, y aquel tiene
 * una docena de dobles de prueba que tendrían que aprender métodos que no usan.
 *
 * <p>El orden es el de la base y no el de {@code UUID.compareTo}; lo único que importa es que el
 * orden, el cursor y el tope usen la misma comparación.
 */
public interface ProductosNoPublicados {

  long contar();

  /** El último id en ese orden, o vacío si no hay ninguno: el tope del borrado. */
  Optional<UUID> ultimo();

  /**
   * Los ids en orden, mayores que {@code despuesDe} —o desde el primero si es nulo— y hasta {@code
   * hasta} inclusive, como mucho {@code limite}. Es un cursor y no una página: el borrado en bloque
   * salta los que conserva, y con páginas numeradas volvería a encontrarlos en cada tanda.
   */
  List<UUID> ids(UUID despuesDe, UUID hasta, int limite);
}
