package co.tecnosport.api.application.envio;

import java.util.Objects;
import java.util.UUID;

/**
 * Qué variante y cuántas, que es todo lo que hace falta para armar los bultos. El peso, las
 * medidas, el valor y la línea de catálogo salen del producto: si el cliente pudiera declararlos,
 * pagaría el flete de una camiseta por una caja de tenis.
 */
public record LineaAEmpacar(UUID varianteId, int cantidad) {

  public LineaAEmpacar {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
  }
}
