package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.proveedores.TallaAlProveedor;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Una página de pedidos como la ve el panel: los pedidos y, aparte, qué talla pedirle al proveedor
 * por las variantes de sus líneas que la nombran distinto (ADR-0085). Aparte y no en la línea,
 * porque la línea es la misma que ve el comprador.
 */
public record PedidosDelPanel(
    PedidosPaginados pedidos, Map<UUID, TallaAlProveedor> tallasAlProveedor) {

  public PedidosDelPanel {
    Objects.requireNonNull(pedidos, "La página de pedidos no puede ser nula.");
    tallasAlProveedor = Map.copyOf(Objects.requireNonNull(tallasAlProveedor));
  }
}
