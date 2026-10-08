package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * De qué configuración de la lista sale una variante, y cuánto le cuesta hoy al negocio.
 *
 * <p>Es lo que deja que la lista de mañana mueva solo el costo y la existencia: la variante no sabe
 * nada de listas —es catálogo—, y el producto guarda un único precio de proveedor, que en
 * tecnología no alcanza porque cada configuración cuesta distinto. El costo no es el precio de
 * venta, y nunca sale al comprador.
 *
 * @param configuracion el id de la configuración de la skill, el mismo de {@link
 *     ConfiguracionTecnologia#sku()}
 */
public record VarianteDeProveedor(
    UUID varianteId,
    UUID productoId,
    UUID proveedorId,
    String configuracion,
    String color,
    Dinero costo,
    Instant actualizadoEn) {

  public VarianteDeProveedor {
    Objects.requireNonNull(varianteId, "El vínculo es de una variante.");
    Objects.requireNonNull(productoId, "El vínculo dice de qué producto es la variante.");
    Objects.requireNonNull(proveedorId, "El vínculo es de un proveedor.");
    Objects.requireNonNull(costo, "El vínculo guarda el costo de la lista.");
    Objects.requireNonNull(actualizadoEn, "El costo tiene fecha.");
    if (configuracion == null || configuracion.isBlank()) {
      throw new ExcepcionDeDominio("El vínculo dice de qué configuración sale la variante.");
    }
    if (color == null || color.isBlank()) {
      throw new ExcepcionDeDominio("El vínculo dice de qué color es la variante.");
    }
    configuracion = configuracion.strip();
    color = color.strip();
  }

  /** El costo de una lista más nueva; una más vieja no lo pisa. */
  public VarianteDeProveedor conCosto(Dinero nuevo, Instant visto) {
    if (visto.isBefore(actualizadoEn)) {
      return this;
    }
    return new VarianteDeProveedor(
        varianteId, productoId, proveedorId, configuracion, color, nuevo, visto);
  }
}
