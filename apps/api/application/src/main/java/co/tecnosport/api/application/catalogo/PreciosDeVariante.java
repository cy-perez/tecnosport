package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.UUID;

/** Los dos precios de una variante, con lo que hace falta para reconocerla en el panel. */
public record PreciosDeVariante(
    UUID varianteId, String sku, String nombreProducto, Dinero contado, Dinero publicado) {

  public PreciosDeVariante {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(sku, "El SKU no puede ser nulo.");
    Objects.requireNonNull(nombreProducto, "El nombre del producto no puede ser nulo.");
    Objects.requireNonNull(contado, "El precio de contado no puede ser nulo.");
    Objects.requireNonNull(publicado, "El precio publicado no puede ser nulo.");
  }
}
