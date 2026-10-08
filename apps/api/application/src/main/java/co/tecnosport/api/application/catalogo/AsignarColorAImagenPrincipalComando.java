package co.tecnosport.api.application.catalogo;

import java.util.Objects;
import java.util.UUID;

/**
 * @param varianteId una variante del producto, o nulo para que la foto valga para todos los tonos
 */
public record AsignarColorAImagenPrincipalComando(UUID productoId, UUID varianteId) {

  public AsignarColorAImagenPrincipalComando {
    Objects.requireNonNull(productoId, "El producto no puede ser nulo.");
  }
}
