package co.tecnosport.api.presentation.carrito.dto;

import java.util.UUID;

/**
 * Sin Bean Validation en este proyecto todavía (no hay proveedor en el classpath) — se valida a
 * mano en el constructor compacto, mismo patrón que los value object de dominio. Spring envuelve
 * cualquier fallo de deserialización del cuerpo (este incluido) en {@code
 * HttpMessageNotReadableException}, que {@code ManejadorDeErrores} ya traduce a 422.
 */
public record AgregarLineaRequest(UUID varianteId, int cantidad) {

  public AgregarLineaRequest {
    if (varianteId == null) {
      throw new IllegalArgumentException("varianteId es obligatorio.");
    }
  }
}
