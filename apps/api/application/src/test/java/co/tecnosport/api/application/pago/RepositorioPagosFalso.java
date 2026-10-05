package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p><b>Guarda y entrega copias</b>, como hace la base: hasta el 4 de octubre de 2026 devolvía la
 * misma instancia que se le guardó, y entonces borrar el {@code guardar} de un caso de uso no hacía
 * fallar ninguna prueba — el objeto del test ya tenía el estado nuevo por referencia. Es la
 * variante de {@code adr/0054} que se corrigió para el inventario y había quedado viva aquí.
 */
final class RepositorioPagosFalso implements RepositorioPagos {

  private final List<Pago> pagos = new ArrayList<>();

  @Override
  public Optional<Pago> buscarPorReferencia(ReferenciaPago referencia) {
    return pagos.stream()
        .filter(p -> p.referencia().equals(referencia))
        .findFirst()
        .map(RepositorioPagosFalso::copia);
  }

  @Override
  public Optional<Pago> buscarPorId(UUID id) {
    return pagos.stream()
        .filter(p -> p.id().equals(id))
        .findFirst()
        .map(RepositorioPagosFalso::copia);
  }

  @Override
  public List<Pago> buscarSinPedidoQueLosEspere() {
    return pagos.stream()
        .filter(p -> p.sinPedidoQueLoEspereDesde().isPresent())
        .map(RepositorioPagosFalso::copia)
        .toList();
  }

  static Pago copia(Pago p) {
    return new Pago(
        p.id(),
        p.pedidoId(),
        p.referencia(),
        p.metodoPago(),
        p.monto(),
        p.estado(),
        p.eventos(),
        p.creadoEn(),
        p.actualizadoEn(),
        p.idTransaccionPasarela().orElse(null),
        p.medioReportadoPorLaPasarela().orElse(null),
        p.sinPedidoQueLoEspereDesde().orElse(null));
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
    return candidatos.size() == 1 ? Optional.of(copia(candidatos.get(0))) : Optional.empty();
  }

  @Override
  public List<Pago> buscarPorPedidoId(UUID pedidoId) {
    return pagos.stream()
        .filter(p -> p.pedidoId().equals(pedidoId))
        .map(RepositorioPagosFalso::copia)
        .toList();
  }

  @Override
  public List<Pago> buscarPendientesParaConciliar(Instant creadosAntesDe) {
    return pagos.stream()
        .filter(p -> p.estado() == EstadoPago.PENDIENTE)
        .filter(p -> p.idTransaccionPasarela().isPresent())
        .filter(p -> p.creadoEn().isBefore(creadosAntesDe))
        .map(RepositorioPagosFalso::copia)
        .toList();
  }

  @Override
  public void guardar(Pago pago) {
    pagos.removeIf(p -> p.id().equals(pago.id()));
    pagos.add(copia(pago));
  }
}
