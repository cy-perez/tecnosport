package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;
import java.util.UUID;

/**
 * Detalle de un producto para el panel admin, cualquier estado. Alimenta la pantalla de edición.
 */
public final class VerProductoAdmin {

  private final RepositorioProductos repositorioProductos;

  public VerProductoAdmin(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public Producto ejecutar(UUID productoId) {
    return repositorioProductos
        .buscarPorId(productoId)
        .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));
  }
}
