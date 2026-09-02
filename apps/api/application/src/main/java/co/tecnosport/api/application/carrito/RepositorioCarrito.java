package co.tecnosport.api.application.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioCarrito {

  Optional<Carrito> buscarPorId(UUID carritoId);

  void guardar(Carrito carrito);
}
