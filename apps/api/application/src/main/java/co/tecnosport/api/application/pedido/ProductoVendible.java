package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.UUID;

/**
 * Si lo que tiene esa variante se puede cobrar hoy. Una sola regla para crear el pedido y para
 * reintentar su pago: hasta el 08/10/2026 el reintento no la miraba, y un pedido en {@code
 * PAGO_FALLIDO} de un modelo que el proveedor ya no tenía se volvía a cobrar.
 */
final class ProductoVendible {

  private ProductoVendible() {}

  /**
   * Publicado no basta: un producto de proveedor que el proveedor ya no tiene sale de la vitrina
   * (oculto por vencimiento o agotado, ADR-0066) aunque siga publicado. `estaEnVitrina` es la misma
   * pregunta que se hace el catálogo público, así que lo que no se lista tampoco se cobra.
   */
  static Producto exigir(RepositorioProductos repositorioProductos, UUID varianteId) {
    Producto producto =
        repositorioProductos
            .buscarPorVarianteId(varianteId)
            .orElseThrow(() -> new VarianteNoEncontradaException(varianteId));
    if (producto.estado() != EstadoProducto.PUBLICADO || !producto.estaEnVitrina()) {
      throw new VarianteNoEncontradaException(varianteId);
    }
    return producto;
  }
}
