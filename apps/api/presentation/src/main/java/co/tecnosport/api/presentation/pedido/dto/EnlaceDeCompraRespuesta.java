package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Un enlace de compra en el panel (ADR-0086). Lleva el token entero: el panel arma con él la URL
 * que se le manda a quien compra, y la vuelve a copiar si el mensaje se perdió.
 *
 * @param producto el nombre del producto; nulo si se borró después
 * @param slug el de la ficha a la que lleva el enlace; nulo por lo mismo
 * @param detalle «Morado · 256 GB»: la variante como la dice el pedido
 * @param estado VIGENTE, USADO, ANULADO o VENCIDO
 */
public record EnlaceDeCompraRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID varianteId,
    String producto,
    String slug,
    String detalle,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String estado,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant creadoEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiraEn,
    UUID pedidoId) {}
