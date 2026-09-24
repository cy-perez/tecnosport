package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.EventoDePagoYaRegistradoException;
import co.tecnosport.api.application.pago.ReferenciaDePagoYaExisteException;
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
  private boolean elEventoYaLoRegistroOtro;

  void limpiar() {
    pagos.clear();
    elEventoYaLoRegistroOtro = false;
  }

  /**
   * La carrera que el repositorio real resuelve contra el índice único de {@code evento_pago}: dos
   * notificaciones de la misma transacción entran a la vez, las dos leen el pago pendiente y la
   * segunda choca al escribir el evento. Aquí no hay base de datos, así que se pide explícitamente
   * — lo que la prueba mira es qué hace el controlador con ese choque, no cómo se produce.
   */
  void simularQueOtraNotificacionYaRegistroElEvento() {
    this.elEventoYaLoRegistroOtro = true;
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
    if (elEventoYaLoRegistroOtro && !pago.eventos().isEmpty()) {
      elEventoYaLoRegistroOtro = false;
      throw new EventoDePagoYaRegistradoException(
          pago.referencia().valor(), pago.eventos().get(pago.eventos().size() - 1).idEvento());
    }
    pagos.removeIf(p -> p.id().equals(pago.id()));
    pagos.add(pago);
  }

  /**
   * La referencia es única en el repositorio real: es la que viaja a la pasarela. Sin esta
   * comprobación el doble acepta dos pagos con la misma y buscarPorReferencia devuelve el que se
   * guardó primero, que puede ser el de otra prueba.
   *
   * <p><b>Lanza lo mismo que el adaptador real, y antes no.</b> Lanzaba {@code
   * IllegalStateException}, o sea que el doble distinguía un caso que el real confundía: allí
   * cualquier violación de integridad salía como {@code EventoDePagoYaRegistradoException}, y por
   * el webhook eso se contesta con un 200. Un doble más correcto que el código al que sustituye no
   * puede enseñar el defecto — es el mismo patrón que dejó muertas dos ramas de 4xx en {@code
   * cuenta}. Ahora los dos lanzan {@link ReferenciaDePagoYaExisteException}, y el mensaje conserva
   * la pista de montaje porque en una prueba casi siempre es eso.
   */
  private void rechazarSiLaReferenciaYaEsDeOtro(Pago pago) {
    boolean ocupada =
        pagos.stream()
            .anyMatch(
                otro ->
                    otro.referencia().equals(pago.referencia()) && !otro.id().equals(pago.id()));
    if (ocupada) {
      throw new ReferenciaDePagoYaExisteException(
          pago.referencia().valor(),
          new IllegalStateException(
              "Si es el montaje de otra prueba, falta limpiarlo entre métodos; si la prueba"
                  + " necesita dos pagos, dales referencias distintas."));
    }
  }
}
