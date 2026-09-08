package co.tecnosport.api.application.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioCarrito {

  Optional<Carrito> buscarPorId(UUID carritoId);

  void guardar(Carrito carrito);

  /**
   * Borra los carritos sin actividad desde {@code limite} y devuelve cuántos se llevó. Es una
   * operación de conjunto a propósito: cargar en memoria todos los carritos viejos para borrarlos
   * uno por uno no aporta nada —no hay regla de negocio que consultar— y escala mal justo cuando
   * más carritos hay que limpiar.
   */
  int eliminarInactivosDesde(Instant limite);
}
