package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param color el nombre de un color de la paleta, o una combinación —«Negro / Vino»—, como lo
 *     escribe la revisión de un borrador
 * @param existencias una por talla del producto; vacía si el producto todavía no tiene color,
 *     porque entonces sus variantes toman el color con la existencia que ya tienen
 */
public record AgregarColorDesdeLaPrincipalComando(
    UUID productoId, String color, List<ExistenciaDelColorNuevo> existencias) {

  public AgregarColorDesdeLaPrincipalComando {
    Objects.requireNonNull(productoId, "El producto no puede ser nulo.");
    Objects.requireNonNull(color, "El color no puede ser nulo.");
    existencias = existencias == null ? List.of() : List.copyOf(existencias);
  }
}
