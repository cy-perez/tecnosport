package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @param contados el precio <b>de contado</b> como tiene que quedar, por id de variante; las que no
 *     estén no se tocan. El publicado no viaja: lo calcula el servidor (ADR-0095)
 */
public record CambiarPreciosComando(UUID productoId, Map<UUID, Dinero> contados) {

  public CambiarPreciosComando {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");
    contados = Map.copyOf(Objects.requireNonNull(contados, "Los precios no pueden ser nulos."));
  }
}
