package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Trabajo programado (docs/11-pagos-y-envios.md: "un trabajo programado concilia los pagos que
 * quedaron pendientes y nunca recibieron webhook. Los webhooks se pierden; el dinero no puede
 * perderse con ellos"). Solo revisa pagos con {@link Pago#idTransaccionWompi()} registrado — sin él
 * no hay cómo consultar la API de Wompi, que busca por su id, no por la referencia propia. Un pago
 * sin ese id (el cliente cerró la pestaña antes de volver del checkout, y tampoco llegó el webhook)
 * queda fuera de este mecanismo, para seguimiento manual en el panel — la vista de operación mínima
 * de esta fase, no construida todavía.
 */
public final class ConciliarPagosPendientes {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final PasarelaDePagos pasarelaDePagos;
  private final Reloj reloj;
  private final Duration antiguedadMinima;

  public ConciliarPagosPendientes(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj,
      Duration antiguedadMinima) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.pasarelaDePagos = Objects.requireNonNull(pasarelaDePagos);
    this.reloj = Objects.requireNonNull(reloj);
    this.antiguedadMinima = Objects.requireNonNull(antiguedadMinima);
  }

  public ResultadoConciliacion ejecutar() {
    Instant ahora = reloj.ahora();
    List<Pago> pendientes =
        repositorioPagos.buscarPendientesParaConciliar(ahora.minus(antiguedadMinima));

    int conciliados = 0;
    for (Pago pago : pendientes) {
      if (conciliar(pago, ahora)) {
        conciliados++;
      }
    }
    return new ResultadoConciliacion(
        pendientes.size(), conciliados, pendientes.size() - conciliados);
  }

  private boolean conciliar(Pago pago, Instant ahora) {
    String idTransaccionWompi = pago.idTransaccionWompi().orElseThrow();
    Optional<String> estadoWompi = pasarelaDePagos.consultarTransaccion(idTransaccionWompi);
    if (estadoWompi.isEmpty()) {
      return false;
    }
    EstadoPago nuevoEstado = EstadosWompi.aEstadoPago(estadoWompi.get());
    if (nuevoEstado == null) {
      return false;
    }
    EventoPago evento =
        new EventoPago(
            "conciliacion:" + idTransaccionWompi + ":" + estadoWompi.get(), nuevoEstado, ahora);
    ResultadoEventoDePago resultado =
        AplicadorDeResultadoDePago.aplicar(
            pago, evento, "conciliacion-wompi", repositorioPagos, repositorioPedidos);
    return resultado == ResultadoEventoDePago.APLICADO;
  }
}
