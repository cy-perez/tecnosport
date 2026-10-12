package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.CambioDePrecio;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.List;
import java.util.Objects;

/**
 * Cambia el precio de venta de una o varias variantes del producto desde la edición del panel. Las
 * reglas —precio mayor que cero, variantes del producto, todo o nada— son de {@link
 * Producto#cambiarPrecios}; aquí solo se carga y se graba.
 *
 * <p>Graba una fila por variante: quien llama lo envuelve en una transacción, como en {@link
 * CambiarTalla}, o una que fallara a mitad dejaría el producto con unos precios nuevos y otros no.
 */
public final class CambiarPrecios {

  private final RepositorioProductos repositorioProductos;

  public CambiarPrecios(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  /**
   * @return lo que cambió, con el precio anterior; vacía si todas ya tenían el precio pedido
   */
  public List<CambioDePrecio> ejecutar(CambiarPreciosComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    List<CambioDePrecio> cambios = producto.cambiarPrecios(comando.precios());
    for (CambioDePrecio cambio : cambios) {
      repositorioProductos.actualizarPrecio(cambio.varianteId(), cambio.nuevo());
    }
    return cambios;
  }
}
