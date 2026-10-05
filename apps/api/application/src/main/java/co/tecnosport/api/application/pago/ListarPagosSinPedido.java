package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import java.util.List;
import java.util.Objects;

/**
 * Los pagos que entraron sin un pedido que los esperara y todavía no se han devuelto: la bandeja
 * del panel. Uno con su reintegro registrado sale de la lista, porque la constancia apunta al pago
 * por {@code origenId}.
 */
public final class ListarPagosSinPedido {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioReintegros repositorioReintegros;

  public ListarPagosSinPedido(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioReintegros = Objects.requireNonNull(repositorioReintegros);
  }

  public List<PagoSinPedido> ejecutar() {
    return repositorioPagos.buscarSinPedidoQueLosEspere().stream()
        .filter(pago -> repositorioReintegros.buscarPorOrigen(pago.id()).isEmpty())
        .flatMap(
            pago ->
                repositorioPedidos.buscarPorId(pago.pedidoId()).stream()
                    .map(pedido -> new PagoSinPedido(pago, pedido)))
        .toList();
  }
}
