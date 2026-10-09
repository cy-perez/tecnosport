package co.tecnosport.api.application.catalogo;

import java.util.Objects;
import java.util.UUID;

/**
 * Cuántas unidades hay del color nuevo en una talla.
 *
 * @param modeloId la variante que ya existe en esa talla —uno de {@code
 *     Producto.modelosSinColor()}—, de la que la nueva copia el precio y el empaque
 */
public record ExistenciaDelColorNuevo(UUID modeloId, int existencia) {

  public ExistenciaDelColorNuevo {
    Objects.requireNonNull(modeloId, "Falta la variante de la talla.");
    if (existencia < 0) {
      throw new IllegalArgumentException("La existencia no puede ser negativa.");
    }
  }
}
