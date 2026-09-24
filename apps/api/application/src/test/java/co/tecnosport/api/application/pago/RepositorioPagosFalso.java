package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.time.Instant;
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

  /** Como el real: exactamente uno o ninguno, porque la columna no lleva `unique`. */
  @Override
  public Optional<Pago> buscarPorIdTransaccionPasarela(String idTransaccionPasarela) {
    if (idTransaccionPasarela == null || idTransaccionPasarela.isBlank()) {
      return Optional.empty();
    }
    List<Pago> candidatos =
        pagos.stream()
            .filter(p -> p.idTransaccionPasarela().map(idTransaccionPasarela::equals).orElse(false))
            .toList();
    return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
  }

  @Override
  public List<Pago> buscarPorPedidoId(UUID pedidoId) {
    return pagos.stream().filter(p -> p.pedidoId().equals(pedidoId)).toList();
  }

  @Override
  public List<Pago> buscarPendientesParaConciliar(Instant creadosAntesDe) {
    return pagos.stream()
        .filter(p -> p.estado() == EstadoPago.PENDIENTE)
        .filter(p -> p.idTransaccionPasarela().isPresent())
        .filter(p -> p.creadoEn().isBefore(creadosAntesDe))
        .toList();
  }

  @Override
  public void guardar(Pago pago) {
    pagos.removeIf(p -> p.id().equals(pago.id()));
    pagos.add(pago);
  }
}
