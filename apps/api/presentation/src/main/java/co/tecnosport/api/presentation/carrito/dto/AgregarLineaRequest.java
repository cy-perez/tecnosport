package co.tecnosport.api.presentation.carrito.dto;

import java.util.UUID;

/**
 * Sin Bean Validation en este proyecto todavía (no hay proveedor en el classpath) — se valida a
 * mano en el constructor compacto, mismo patrón que los value object de dominio. Spring envuelve
 * cualquier fallo de deserialización del cuerpo (este incluido) en {@code
 * HttpMessageNotReadableException}, que {@code ManejadorDeErrores} ya traduce a 422.
 *
 * @param enlaceCompra el token del enlace de compra de un color de tecnología confirmado con el
 *     proveedor (ADR-0086); con él la línea queda con una unidad. Nulo en una línea corriente
 */
public record AgregarLineaRequest(UUID varianteId, int cantidad, String enlaceCompra) {

  public AgregarLineaRequest(UUID varianteId, int cantidad) {
    this(varianteId, cantidad, null);
  }

  public AgregarLineaRequest {
    if (varianteId == null) {
      throw new IllegalArgumentException("varianteId es obligatorio.");
    }
  }
}
