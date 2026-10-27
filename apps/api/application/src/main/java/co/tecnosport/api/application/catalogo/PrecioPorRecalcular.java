package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.UUID;

/**
 * Una variante cuyo publicado no es el que dan las tarifas de hoy: el que tiene y el que tendría.
 */
public record PrecioPorRecalcular(
    UUID varianteId,
    String sku,
    String nombreProducto,
    Dinero contado,
    Dinero publicadoActual,
    Dinero publicadoNuevo) {

  public PrecioPorRecalcular {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(sku, "El SKU no puede ser nulo.");
    Objects.requireNonNull(nombreProducto, "El nombre del producto no puede ser nulo.");
    Objects.requireNonNull(contado, "El precio de contado no puede ser nulo.");
    Objects.requireNonNull(publicadoActual, "El publicado actual no puede ser nulo.");
    Objects.requireNonNull(publicadoNuevo, "El publicado nuevo no puede ser nulo.");
  }
}
