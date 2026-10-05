package co.tecnosport.api.presentation.pago.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Un pago aprobado sin pedido que lo esperara, para la bandeja del panel. {@code monto} es lo que
 * se va a devolver, entero: el panel lo muestra y no lo manda de vuelta.
 */
public record PagoSinPedidoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID pagoId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String referencia,
    @Schema(requiredMode = RequiredMode.REQUIRED) String metodoPago,
    @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta monto,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant desde,
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID pedidoId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String numeroPedido,
    @Schema(requiredMode = RequiredMode.REQUIRED) String estadoPedido,
    @Schema(requiredMode = RequiredMode.REQUIRED) String correo) {}
