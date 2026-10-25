package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la ficha sabe de un enlace de compra vigente que llega en su URL: qué variante habilita y
 * hasta cuándo. Nada de quién lo pidió ni del pedido.
 */
public record EnlaceDeCompraPublicoRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID varianteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiraEn) {}
