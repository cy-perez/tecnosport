package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.SetRotacion;
import java.util.Objects;
import java.util.UUID;

/**
 * Borra el set, sus fotogramas y los objetos que ocupaban en el bucket. En cualquier estado: es la
 * salida cuando una captura sale mal y también el paso previo obligatorio para reemplazar un set ya
 * publicado.
 *
 * <p><strong>Los objetos se borran antes que la fila</strong>, y el orden importa. Al revés —fila
 * primero, objetos después— un fallo a mitad de camino deja los objetos huérfanos para siempre:
 * reintentar el borrado responde 404 y ya nadie sabe qué prefijo había que limpiar. Así, un fallo
 * deja el set sin poder mostrarse pero todavía en la base, y reintentar termina el trabajo: borrar
 * por prefijo es idempotente. Un set en camino a desaparecer que se ve roto unos segundos es mejor
 * que almacenamiento que se paga para siempre.
 *
 * <p><strong>Borrar bytes deja de ser irreversible gracias al versionado de objetos del
 * bucket</strong> (docs/07-infra-gcp.md: "un borrado accidental de un set de rotación son quince
 * fotos que hay que volver a tomar"). Ese versionado es la red que hace aceptable este borrado; sin
 * él, esto sería destructivo de verdad.
 */
public final class EliminarSetRotacion {

  private final RepositorioSetsRotacion repositorioSetsRotacion;
  private final AlmacenDeImagenes almacenDeImagenes;

  public EliminarSetRotacion(
      RepositorioSetsRotacion repositorioSetsRotacion, AlmacenDeImagenes almacenDeImagenes) {
    this.repositorioSetsRotacion = Objects.requireNonNull(repositorioSetsRotacion);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
  }

  /** Devuelve cuántos objetos se borraron del bucket, para que quien llame lo registre. */
  public int ejecutar(UUID setId) {
    Objects.requireNonNull(setId, "El id del set no puede ser nulo.");

    SetRotacion set =
        repositorioSetsRotacion
            .buscarPorId(setId)
            .orElseThrow(() -> new SetRotacionNoEncontradoException(setId));

    int borrados = almacenDeImagenes.eliminarPorPrefijo(ClavesDeRotacion.prefijoDe(set));
    repositorioSetsRotacion.eliminar(setId);
    return borrados;
  }
}
