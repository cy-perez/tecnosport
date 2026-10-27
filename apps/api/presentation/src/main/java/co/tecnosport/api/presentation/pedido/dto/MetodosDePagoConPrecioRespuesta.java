package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.Objects;

/**
 * Los medios de pago que se le ofrecen a este carrito, cada uno con lo que el comprador pagaría de
 * mercancía si lo elige (ADR-0095). Ordenados por nombre, como {@code
 * /metodos-de-pago-disponibles}. Un objeto y no un arreglo suelto para que crezca sin romper a
 * quien lo lee.
 */
public record MetodosDePagoConPrecioRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<MetodoDePagoConPrecioRespuesta> metodos) {

  public MetodosDePagoConPrecioRespuesta {
    metodos = List.copyOf(Objects.requireNonNull(metodos));
  }

  /**
   * @param subtotal la mercancía al precio de este medio, sin flete: el publicado con pasarela, el
   *     de contado sin ella
   * @param descuento lo que este medio ahorra frente al precio publicado; cero con pasarela
   */
  // Con un nombre único y no `Metodo`: springdoc nombra el esquema por el nombre simple del record,
  // y uno tan genérico choca con el primero que se llame igual en otro paquete.
  public record MetodoDePagoConPrecioRespuesta(
      @Schema(requiredMode = RequiredMode.REQUIRED) MetodoPago metodo,
      @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta subtotal,
      @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta descuento) {}
}
