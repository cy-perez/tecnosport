package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * No existe una categoría con ese id — el panel admin intentó crear un producto contra una que no
 * existe.
 */
public final class CategoriaNoEncontradaException extends RuntimeException {

  public CategoriaNoEncontradaException(UUID id) {
    super("No existe una categoría con id '" + id + "'.");
  }
}
