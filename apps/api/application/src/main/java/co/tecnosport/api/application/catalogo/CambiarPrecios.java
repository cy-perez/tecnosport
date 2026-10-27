package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.CambioDePrecio;
import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.List;
import java.util.Objects;

/**
 * Cambia el precio de una o varias variantes del producto desde la edición del panel. Lo que llega
 * es el <b>precio de contado</b>; el publicado lo calcula la {@link ComisionDePasarela} (ADR-0095).
 * Las reglas —precio mayor que cero, variantes del producto, todo o nada— son de {@link
 * Producto#cambiarPrecios}; aquí solo se carga y se graba.
 *
 * <p>Graba una fila por variante: quien llama lo envuelve en una transacción, como en {@link
 * CambiarTalla}, o una que fallara a mitad dejaría el producto con unos precios nuevos y otros no.
 */
public final class CambiarPrecios {

  private final RepositorioProductos repositorioProductos;
  private final ComisionDePasarela comision;

  public CambiarPrecios(RepositorioProductos repositorioProductos, ComisionDePasarela comision) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.comision = Objects.requireNonNull(comision);
  }

  /**
   * @return lo que cambió, con los precios anteriores; vacía si todas ya tenían el contado pedido
   */
  public List<CambioDePrecio> ejecutar(CambiarPreciosComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    List<CambioDePrecio> cambios = producto.cambiarPrecios(comando.contados(), comision);
    for (CambioDePrecio cambio : cambios) {
      repositorioProductos.actualizarPrecios(
          cambio.varianteId(), cambio.contadoNuevo(), cambio.publicadoNuevo());
    }
    return cambios;
  }
}
