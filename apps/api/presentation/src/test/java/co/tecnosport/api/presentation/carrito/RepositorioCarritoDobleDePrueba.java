package co.tecnosport.api.presentation.carrito;

import co.tecnosport.api.application.carrito.RepositorioCarrito;
import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioCarritoDobleDePrueba implements RepositorioCarrito {

  private final Map<UUID, Carrito> carritos = new HashMap<>();

  @Override
  public Optional<Carrito> buscarPorId(UUID carritoId) {
    return Optional.ofNullable(carritos.get(carritoId));
  }

  @Override
  public void guardar(Carrito carrito) {
    carritos.put(carrito.id(), carrito);
  }

  /**
   * La purga no tiene endpoint: corre en una tarea programada, no en un controlador. Aquí no hay
   * nada que probar, así que el doble solo cumple el contrato.
   */
  @Override
  public int eliminarInactivosDesde(Instant limite) {
    return 0;
  }
}
