package co.tecnosport.api.application.catalogo;

import java.util.Objects;
import java.util.UUID;

/**
 * Borra el set y sus fotogramas, en cualquier estado: es la salida cuando una captura sale mal y
 * también el paso previo obligatorio para reemplazar un set ya publicado.
 *
 * <p>Los objetos del bucket no se borran, mismo criterio que al reemplazar la imagen principal: el
 * bucket tiene versionado y borrar bytes es irreversible (docs/07-infra-gcp.md).
 */
public final class EliminarSetRotacion {

  private final RepositorioSetsRotacion repositorioSetsRotacion;

  public EliminarSetRotacion(RepositorioSetsRotacion repositorioSetsRotacion) {
    this.repositorioSetsRotacion = Objects.requireNonNull(repositorioSetsRotacion);
  }

  public void ejecutar(UUID setId) {
    Objects.requireNonNull(setId, "El id del set no puede ser nulo.");

    repositorioSetsRotacion
        .buscarPorId(setId)
        .orElseThrow(() -> new SetRotacionNoEncontradoException(setId));
    repositorioSetsRotacion.eliminar(setId);
  }
}
