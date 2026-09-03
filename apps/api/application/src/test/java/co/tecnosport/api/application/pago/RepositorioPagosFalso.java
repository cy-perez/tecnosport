package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioPagosFalso implements RepositorioPagos {

  private final List<Pago> pagos = new ArrayList<>();

  @Override
  public Optional<Pago> buscarPorReferencia(ReferenciaPago referencia) {
    return pagos.stream().filter(p -> p.referencia().equals(referencia)).findFirst();
  }

  @Override
  public List<Pago> buscarPorPedidoId(UUID pedidoId) {
    return pagos.stream().filter(p -> p.pedidoId().equals(pedidoId)).toList();
  }

  @Override
  public void guardar(Pago pago) {
    pagos.removeIf(p -> p.id().equals(pago.id()));
    pagos.add(pago);
  }
}
