package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/** Los colores y el precio de venta de las configuraciones que se nombran. */
public record ElegirConfiguracionesPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EleccionPeticion> configuraciones) {

  public ElegirConfiguracionesPeticion {
    Objects.requireNonNull(configuraciones, "Hay que decir qué configuraciones se eligen.");
  }

  public List<BorradorTecnologia.Eleccion> aDominio() {
    return configuraciones.stream()
        .map(
            e ->
                new BorradorTecnologia.Eleccion(
                    e.sku(),
                    e.colores(),
                    e.precioVenta() == null ? null : Dinero.deCop(e.precioVenta())))
        .toList();
  }

  /**
   * @param colores los que se venden; vacío es «esta configuración no se vende»
   */
  public record EleccionPeticion(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sku,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> colores,
      Long precioVenta) {
    public EleccionPeticion {
      Objects.requireNonNull(sku, "La elección dice de qué configuración es.");
      Objects.requireNonNull(colores, "La elección dice qué colores, aunque sean ninguno.");
    }
  }
}
