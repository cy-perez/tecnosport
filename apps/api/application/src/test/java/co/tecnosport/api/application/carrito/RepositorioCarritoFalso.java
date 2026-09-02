package co.tecnosport.api.application.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioCarritoFalso implements RepositorioCarrito {

  private final Map<UUID, Carrito> carritos = new HashMap<>();

  @Override
  public Optional<Carrito> buscarPorId(UUID carritoId) {
    return Optional.ofNullable(carritos.get(carritoId));
  }

  @Override
  public void guardar(Carrito carrito) {
    carritos.put(carrito.id(), carrito);
  }
}
