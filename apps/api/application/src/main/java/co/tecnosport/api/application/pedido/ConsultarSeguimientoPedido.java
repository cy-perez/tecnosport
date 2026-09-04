package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/**
 * Seguimiento sin sesión ({@code GET /pedidos/{id}/seguimiento}, "con token del correo, sin
 * sesión", docs/03-api.md). No hay verificación de correo ni envío de enlaces firmados todavía
 * (Fase 4, docs/09-plan-de-arranque.md — tampoco existe infraestructura de correo transaccional
 * hoy): el "token" es el correo mismo, que junto con el id del pedido (un UUID, no adivinable) es
 * la prueba de que quien consulta es quien hizo el pedido.
 *
 * <p>Un correo que no coincide se trata exactamente igual que un id inexistente — {@link
 * PedidoNoEncontradoException} en los dos casos, con el mismo mensaje— para no filtrar si el id
 * existe a quien no conoce el correo real.
 */
public final class ConsultarSeguimientoPedido {

  private final RepositorioPedidos repositorioPedidos;

  public ConsultarSeguimientoPedido(RepositorioPedidos repositorioPedidos) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
  }

  public Pedido ejecutar(ConsultarSeguimientoPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    String correoNormalizado =
        comando.correo() == null ? "" : comando.correo().trim().toLowerCase();
    if (!pedido.correo().valor().equals(correoNormalizado)) {
      throw new PedidoNoEncontradoException(comando.pedidoId());
    }
    return pedido;
  }
}
