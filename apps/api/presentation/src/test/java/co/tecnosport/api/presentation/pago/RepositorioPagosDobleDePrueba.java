package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RepositorioPagosDobleDePrueba implements RepositorioPagos {

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
  public List<Pago> buscarPendientesParaConciliar(Instant creadosAntesDe) {
    return pagos.stream()
        .filter(p -> p.estado() == EstadoPago.PENDIENTE)
        .filter(p -> p.idTransaccionWompi().isPresent())
        .filter(p -> p.creadoEn().isBefore(creadosAntesDe))
        .toList();
  }

  @Override
  public void guardar(Pago pago) {
    pagos.removeIf(p -> p.id().equals(pago.id()));
    pagos.add(pago);
  }
}
