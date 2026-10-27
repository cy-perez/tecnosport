package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Un pago de Wompi pendiente y sin id de transacción, para la alerta del panel. {@code referencia}
 * es lo que se busca en el portal de Wompi.
 */
public record PagoSinTransaccionRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID pagoId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String referencia,
    @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta monto,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant creadoEn,
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID pedidoId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String numeroPedido,
    @Schema(requiredMode = RequiredMode.REQUIRED) String estadoPedido,
    @Schema(requiredMode = RequiredMode.REQUIRED) String correo) {}
