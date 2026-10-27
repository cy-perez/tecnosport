package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.UUID;

/**
 * Una fila de la previsualización del recálculo tal como la vio quien lo confirmó: el contado y el
 * publicado que tenía la variante, y el publicado nuevo que se le enseñó. {@link
 * RecalcularPreciosPublicados#aplicar} solo graba lo que sigue siendo así.
 */
public record PrecioRevisado(
    UUID varianteId, Dinero contado, Dinero publicadoActual, Dinero publicadoNuevo) {

  public PrecioRevisado {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(contado, "El precio de contado no puede ser nulo.");
    Objects.requireNonNull(publicadoActual, "El publicado actual no puede ser nulo.");
    Objects.requireNonNull(publicadoNuevo, "El publicado nuevo no puede ser nulo.");
  }
}
