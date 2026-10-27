package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.UUID;

/** Un pedido detenido en preparación, para la alerta del panel. {@code desde} es su llegada. */
public record PedidoDetenidoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID pedidoId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String numeroPedido,
    @Schema(requiredMode = RequiredMode.REQUIRED) String correo,
    @Schema(requiredMode = RequiredMode.REQUIRED) String tipoEntrega,
    @Schema(requiredMode = RequiredMode.REQUIRED) String metodoPago,
    @Schema(requiredMode = RequiredMode.REQUIRED) Instant desde) {}
