package co.tecnosport.api.presentation.carrito.dto;

import java.util.UUID;

/**
 * @param enlaceCompra el token del enlace de compra con que entró la línea, si lo trajo (ADR-0086):
 *     el checkout lo devuelve al crear el pedido
 */
public record LineaCarritoRespuesta(UUID id, UUID varianteId, int cantidad, String enlaceCompra) {

  public LineaCarritoRespuesta(UUID id, UUID varianteId, int cantidad) {
    this(id, varianteId, cantidad, null);
  }
}
