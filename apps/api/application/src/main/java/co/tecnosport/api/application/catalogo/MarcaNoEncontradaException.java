package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * No existe una marca con ese id — el panel admin intentó crear un producto contra una que no
 * existe.
 */
public final class MarcaNoEncontradaException extends RuntimeException {

  public MarcaNoEncontradaException(UUID id) {
    super("No existe una marca con id '" + id + "'.");
  }
}
