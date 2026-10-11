package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.CierreDePublicacion;
import co.tecnosport.api.domain.proveedores.EstadoPublicacionProveedor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una publicación del lote que no dejó borrador, como la lista el panel: cuándo, cómo empezaba el
 * anuncio y por qué se cerró.
 *
 * @param extracto el comienzo del texto del anuncio; nulo si el anuncio son solo fotos
 * @param fotos cuántas fotos traía
 */
public record PublicacionCerrada(
    UUID id,
    Instant fecha,
    EstadoPublicacionProveedor estado,
    CierreDePublicacion cierre,
    String extracto,
    int fotos) {

  public PublicacionCerrada {
    Objects.requireNonNull(id);
    Objects.requireNonNull(fecha);
    Objects.requireNonNull(estado);
    Objects.requireNonNull(cierre);
  }
}
