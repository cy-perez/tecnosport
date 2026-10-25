package co.tecnosport.api.application.carrito;

import java.util.UUID;

/**
 * @param enlaceCompra el token del enlace de compra de un color confirmado con el proveedor
 *     (ADR-0086); nulo en una línea corriente. Con enlace la línea queda con una unidad
 */
public record AgregarLineaAlCarritoComando(
    UUID carritoId, UUID varianteId, int cantidad, String enlaceCompra) {

  public AgregarLineaAlCarritoComando(UUID carritoId, UUID varianteId, int cantidad) {
    this(carritoId, varianteId, cantidad, null);
  }
}
