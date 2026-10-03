package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.inventario.VariantesDisponibles;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.List;
import java.util.Objects;

/**
 * La ficha de un producto publicado con la disponibilidad de sus variantes al lado, y la escala de
 * tallas de su categoría —la propia o la de su rama— para que la ficha enseñe todas las tallas, con
 * las que este producto no trae tachadas.
 */
public record FichaDeProducto(
    Producto producto, VariantesDisponibles disponibles, List<String> escalaTallas) {

  public FichaDeProducto {
    Objects.requireNonNull(producto, "El producto de la ficha no puede ser nulo.");
    Objects.requireNonNull(disponibles, "La disponibilidad no puede ser nula.");
    escalaTallas = escalaTallas == null ? List.of() : List.copyOf(escalaTallas);
  }

  public FichaDeProducto(Producto producto, VariantesDisponibles disponibles) {
    this(producto, disponibles, List.of());
  }
}
