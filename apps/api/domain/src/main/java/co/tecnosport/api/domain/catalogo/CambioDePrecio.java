package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import java.util.Objects;
import java.util.UUID;

/**
 * Los precios que una variante tenía y los que tiene ahora: el de contado, que es el que cambia
 * quien edita, y el publicado, que sale de él (ADR-0095). Lleva los anteriores porque quien lo
 * registra los necesita y, una vez aplicado el cambio, el agregado ya no los tiene.
 */
public record CambioDePrecio(
    UUID varianteId,
    Sku sku,
    Dinero contadoAnterior,
    Dinero contadoNuevo,
    Dinero publicadoAnterior,
    Dinero publicadoNuevo) {

  public CambioDePrecio {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(sku, "El SKU no puede ser nulo.");
    Objects.requireNonNull(contadoAnterior, "El precio de contado anterior no puede ser nulo.");
    Objects.requireNonNull(contadoNuevo, "El precio de contado nuevo no puede ser nulo.");
    Objects.requireNonNull(publicadoAnterior, "El precio publicado anterior no puede ser nulo.");
    Objects.requireNonNull(publicadoNuevo, "El precio publicado nuevo no puede ser nulo.");
  }
}
