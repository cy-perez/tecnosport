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

  void limpiar() {
    pagos.clear();
  }

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
        .filter(p -> p.idTransaccionPasarela().isPresent())
        .filter(p -> p.creadoEn().isBefore(creadosAntesDe))
        .toList();
  }

  @Override
  public void guardar(Pago pago) {
    rechazarSiLaReferenciaYaEsDeOtro(pago);
    pagos.removeIf(p -> p.id().equals(pago.id()));
    pagos.add(pago);
  }

  /**
   * La referencia es única en el repositorio real: es la que viaja a la pasarela. Sin esta
   * comprobación el doble acepta dos pagos con la misma y buscarPorReferencia devuelve el que se
   * guardó primero, que puede ser el de otra prueba.
   */
  private void rechazarSiLaReferenciaYaEsDeOtro(Pago pago) {
    boolean ocupada =
        pagos.stream()
            .anyMatch(
                otro ->
                    otro.referencia().equals(pago.referencia()) && !otro.id().equals(pago.id()));
    if (ocupada) {
      throw new IllegalStateException(
          "Ya hay otro pago con la referencia "
              + pago.referencia().valor()
              + " en el doble de prueba. Si es el montaje de otra prueba, falta limpiarlo entre"
              + " métodos; si la prueba necesita dos pagos, dales referencias distintas.");
    }
  }
}
