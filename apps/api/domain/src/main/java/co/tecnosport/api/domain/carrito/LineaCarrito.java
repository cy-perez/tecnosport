package co.tecnosport.api.domain.carrito;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Objects;
import java.util.UUID;

public record LineaCarrito(UUID id, UUID varianteId, int cantidad) {

  public LineaCarrito {
    Objects.requireNonNull(id, "El id de la línea no puede ser nulo.");
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio("La cantidad de una línea de carrito debe ser mayor que cero.");
    }
  }

  public LineaCarrito conCantidad(int nuevaCantidad) {
    return new LineaCarrito(id, varianteId, nuevaCantidad);
  }
}
