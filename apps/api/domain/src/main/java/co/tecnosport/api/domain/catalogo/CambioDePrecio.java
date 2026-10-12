package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import java.util.Objects;
import java.util.UUID;

/**
 * El precio que una variante tenía y el que tiene ahora. Lleva el anterior porque quien lo registra
 * lo necesita y, una vez aplicado el cambio, el agregado ya no lo tiene.
 */
public record CambioDePrecio(UUID varianteId, Sku sku, Dinero anterior, Dinero nuevo) {

  public CambioDePrecio {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(sku, "El SKU no puede ser nulo.");
    Objects.requireNonNull(anterior, "El precio anterior no puede ser nulo.");
    Objects.requireNonNull(nuevo, "El precio nuevo no puede ser nulo.");
  }
}
