package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.domain.pedido.MedioDePagoEnPunto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * El número de pedido que mostró quien vino a recoger, y cómo pagó si el pedido se paga en el punto
 * (ADR-0094). Que el número coincida y que el medio corresponda lo decide el caso de uso.
 */
public record EntregarEnPuntoRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String numeroPedido,
    MedioDePagoEnPunto medioDePago) {

  public EntregarEnPuntoRequest {
    Objects.requireNonNull(numeroPedido, "El número de pedido es obligatorio.");
  }
}
