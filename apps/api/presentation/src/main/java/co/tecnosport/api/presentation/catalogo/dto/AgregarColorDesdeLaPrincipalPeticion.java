package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un color nuevo para la foto principal: el nombre de la paleta y, si el producto ya tiene otros
 * colores, cuántas unidades hay de él en cada talla. Sin colores todavía, {@code existencias} va
 * vacía: las variantes que ya existen toman el color con la existencia que tienen.
 */
public record AgregarColorDesdeLaPrincipalPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String color,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExistenciaPorTalla> existencias) {

  public AgregarColorDesdeLaPrincipalPeticion {
    Objects.requireNonNull(color, "Falta el color.");
    Objects.requireNonNull(existencias, "Faltan las existencias por talla.");
  }

  /**
   * @param modeloId la variante de esa talla que ya existe, de la que la nueva copia el precio
   */
  public record ExistenciaPorTalla(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID modeloId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int existencia) {

    public ExistenciaPorTalla {
      Objects.requireNonNull(modeloId, "Falta la variante de la talla.");
    }
  }
}
