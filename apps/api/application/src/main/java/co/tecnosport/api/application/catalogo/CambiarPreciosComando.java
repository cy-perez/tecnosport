package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @param precios el precio de venta como tiene que quedar, por id de variante; las que no estén no
 *     se tocan
 */
public record CambiarPreciosComando(UUID productoId, Map<UUID, Dinero> precios) {

  public CambiarPreciosComando {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");
    precios = Map.copyOf(Objects.requireNonNull(precios, "Los precios no pueden ser nulos."));
  }
}
