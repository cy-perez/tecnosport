package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import java.util.List;
import java.util.Objects;

/**
 * Corrige la talla de un modelo del producto en todos sus colores: «S-M» pasa a «M» en la negra y
 * en la vino. Las reglas —que no queden dos variantes iguales, que la talla esté permitida, que el
 * SKU no cambie— son de {@link Producto#cambiarTalla}; aquí solo se carga y se graba.
 *
 * <p>Graba una fila por variante: quien llama lo envuelve en una transacción, o una que fallara a
 * mitad dejaría el modelo con la talla nueva en un color y la vieja en otro.
 */
public final class CambiarTalla {

  private final RepositorioProductos repositorioProductos;

  public CambiarTalla(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  /**
   * @return las variantes que cambiaron; vacía si la talla era la misma
   */
  public List<Variante> ejecutar(CambiarTallaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    List<Variante> cambiadas = producto.cambiarTalla(comando.modeloId(), comando.talla());
    for (Variante variante : cambiadas) {
      repositorioProductos.reemplazarAtributoDeVariante(
          variante.id(), variante.talla().orElseThrow());
    }
    return cambiadas;
  }
}
