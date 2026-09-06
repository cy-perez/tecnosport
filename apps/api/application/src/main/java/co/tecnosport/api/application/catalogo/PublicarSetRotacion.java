package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuarto y último paso: el set COMPLETO se publica y desde ese momento la ficha muestra el visor.
 * Es un paso aparte de {@link CompletarSetRotacion} a propósito — entre los dos está la revisión
 * del set entero en el asistente (docs/10-captura-360.md, paso 6), que es donde se caza el
 * fotograma torcido.
 *
 * <p>Un producto tiene a lo sumo un set publicado: publicar sobre uno que ya lo está se rechaza en
 * vez de dejar dos, porque con dos cuál se ve sería cuestión de suerte.
 */
public final class PublicarSetRotacion {

  private final RepositorioSetsRotacion repositorioSetsRotacion;

  public PublicarSetRotacion(RepositorioSetsRotacion repositorioSetsRotacion) {
    this.repositorioSetsRotacion = Objects.requireNonNull(repositorioSetsRotacion);
  }

  public SetRotacion ejecutar(UUID setId) {
    Objects.requireNonNull(setId, "El id del set no puede ser nulo.");

    SetRotacion set =
        repositorioSetsRotacion
            .buscarPorId(setId)
            .orElseThrow(() -> new SetRotacionNoEncontradoException(setId));

    repositorioSetsRotacion
        .buscarPublicadoDeProducto(set.productoId())
        .filter(publicado -> !publicado.id().equals(set.id()))
        .ifPresent(
            publicado -> {
              throw new SetRotacionPublicadoExistenteException(set.productoId(), publicado.id());
            });

    set.publicar();
    repositorioSetsRotacion.actualizar(set);
    return set;
  }
}
