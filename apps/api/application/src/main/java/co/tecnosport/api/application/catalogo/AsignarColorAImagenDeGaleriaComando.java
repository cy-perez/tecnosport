package co.tecnosport.api.application.catalogo;

import java.util.Objects;
import java.util.UUID;

/**
 * @param varianteId una variante del producto, o nulo para que la foto valga para todos los tonos
 */
public record AsignarColorAImagenDeGaleriaComando(UUID productoId, UUID imagenId, UUID varianteId) {

  public AsignarColorAImagenDeGaleriaComando {
    Objects.requireNonNull(productoId, "El producto no puede ser nulo.");
    Objects.requireNonNull(imagenId, "La imagen no puede ser nula.");
  }
}
