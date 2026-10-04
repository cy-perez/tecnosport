package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.EliminarProducto;
import co.tecnosport.api.application.catalogo.ProductoConVentasException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.ProductoPublicadoException;
import java.util.Objects;
import java.util.UUID;

/**
 * El puerto cumplido con {@link EliminarProducto}. Traduce a {@code false} las dos negativas que
 * significan «este producto se queda» —publicado, o ya vendido— para que no tumben el borrado del
 * lote entero; cualquier otra excepción sí lo tumba, porque es un fallo y no una regla.
 */
public final class EliminacionDeProductosDelCatalogo implements EliminacionDeProductos {

  private final EliminarProducto eliminarProducto;

  public EliminacionDeProductosDelCatalogo(EliminarProducto eliminarProducto) {
    this.eliminarProducto = Objects.requireNonNull(eliminarProducto);
  }

  @Override
  public boolean eliminarSiSePuede(UUID productoId) {
    try {
      eliminarProducto.ejecutar(productoId);
      return true;
    } catch (ProductoPublicadoException | ProductoConVentasException e) {
      return false;
    } catch (ProductoNoEncontradoPorIdException e) {
      // Ya no está: lo que se quería ya pasó.
      return true;
    }
  }
}
