package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}: precio, SKU, nombre e imagen los
 * decide el servidor con el catálogo real, nunca lo que traiga el cliente (docs/00-producto.md).
 */
public record CrearPedidoComando(
    UUID usuarioId,
    String correo,
    List<LineaComando> lineas,
    TipoEntrega tipoEntrega,
    Direccion direccion,
    MetodoPago metodoPago) {

  public record LineaComando(UUID varianteId, int cantidad) {}
}
