package co.tecnosport.api.domain.carrito;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.UUID;

/**
 * @param enlaceCompra el token del enlace de compra con que entró, si el color de la variante
 *     estaba por confirmar con el proveedor (ADR-0086); nulo en las demás. El checkout lo presenta
 *     al crear el pedido, y es el servidor el que decide si sigue vigente
 */
public record LineaCarrito(UUID id, UUID varianteId, int cantidad, String enlaceCompra) {

  public LineaCarrito(UUID id, UUID varianteId, int cantidad) {
    this(id, varianteId, cantidad, null);
  }

  public LineaCarrito {
    enlaceCompra = enlaceCompra == null || enlaceCompra.isBlank() ? null : enlaceCompra.strip();
    Objects.requireNonNull(id, "El id de la línea no puede ser nulo.");
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio("La cantidad de una línea de carrito debe ser mayor que cero.");
    }
  }

  public LineaCarrito conCantidad(int nuevaCantidad) {
    return new LineaCarrito(id, varianteId, nuevaCantidad, enlaceCompra);
  }
}
