package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Las imágenes de la galería, todas, en el orden en que tienen que quedar.
 *
 * <p>La lista entera y no "mueve esta al puesto 2": dos pantallas abiertas sobre el mismo producto
 * mandarían movimientos que se pisan y ganaría el último, sin que nadie se entere. Diciendo el
 * orden completo, el segundo en llegar habla de una galería que ya no es la que vio, y eso se puede
 * detectar.
 *
 * <p>El {@code requireNonNull} es quien de verdad protege —aquí no hay Bean Validation— y el
 * {@code @Schema} es quien hace que el contrato publicado diga que el campo es obligatorio.
 */
public record ReordenarGaleriaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> imagenIds) {

  public ReordenarGaleriaPeticion {
    Objects.requireNonNull(imagenIds, "Hay que decir en qué orden quedan las imágenes.");
  }
}
