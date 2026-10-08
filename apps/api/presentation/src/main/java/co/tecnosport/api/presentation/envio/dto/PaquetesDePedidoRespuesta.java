package co.tecnosport.api.presentation.envio.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * Lo que hay que escribir en el formulario de la plataforma para crear la guía a mano ({@code
 * adr/0071}): un renglón por paquete, en orden. {@code conRecaudo} dice si los valores declarados
 * ya llevan repartido el flete —contraentrega—, que es lo que hace que la suma sea el total a
 * cobrar en la puerta.
 */
public record PaquetesDePedidoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<PaqueteRespuesta> paquetes,
    @Schema(requiredMode = RequiredMode.REQUIRED) boolean conRecaudo) {

  /** El peso en kilos enteros, como lo pide el formulario; las medidas en centímetros. */
  public record PaqueteRespuesta(
      @Schema(requiredMode = RequiredMode.REQUIRED) int pesoKg,
      @Schema(requiredMode = RequiredMode.REQUIRED) int largoCm,
      @Schema(requiredMode = RequiredMode.REQUIRED) int anchoCm,
      @Schema(requiredMode = RequiredMode.REQUIRED) int altoCm,
      @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta valorDeclarado,
      @Schema(requiredMode = RequiredMode.REQUIRED) String contenido) {}
}
