package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/** La alerta con su umbral en días, el que aplicó el servidor. */
public record PedidosDetenidosRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) long umbralDias,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<PedidoDetenidoRespuesta> pedidos) {}
