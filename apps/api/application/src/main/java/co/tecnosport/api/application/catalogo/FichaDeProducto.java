package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.inventario.VariantesDisponibles;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

/** La ficha de un producto publicado con la disponibilidad de sus variantes al lado. */
public record FichaDeProducto(Producto producto, VariantesDisponibles disponibles) {

  public FichaDeProducto {
    Objects.requireNonNull(producto, "El producto de la ficha no puede ser nulo.");
    Objects.requireNonNull(disponibles, "La disponibilidad no puede ser nula.");
  }
}
