package co.tecnosport.api.presentation.carrito.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param cantidadMaximaPorLinea cuántas unidades de una misma variante caben en una línea: el
 *     selector de cantidad del carrito se limita con esto y no con un número propio, porque el tope
 *     es configuración del servidor y el servidor lo exige igual
 */
public record CarritoRespuesta(
    UUID id,
    UUID usuarioId,
    List<LineaCarritoRespuesta> lineas,
    Instant creadoEn,
    @Schema(requiredMode = RequiredMode.REQUIRED) int cantidadMaximaPorLinea) {}
