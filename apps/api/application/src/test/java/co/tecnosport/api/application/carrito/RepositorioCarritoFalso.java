package co.tecnosport.api.application.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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

  @Override
  public int eliminarInactivosDesde(Instant limite) {
    List<UUID> vencidos =
        carritos.values().stream()
            .filter(c -> c.actualizadoEn().isBefore(limite))
            .map(Carrito::id)
            .toList();
    vencidos.forEach(carritos::remove);
    return vencidos.size();
  }

  int cantidad() {
    return carritos.size();
  }
}
