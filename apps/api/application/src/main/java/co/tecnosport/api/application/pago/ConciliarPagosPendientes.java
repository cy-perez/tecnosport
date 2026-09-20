package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Trabajo programado (docs/11-pagos-y-envios.md: "un trabajo programado concilia los pagos que
 * quedaron pendientes y nunca recibieron webhook. Los webhooks se pierden; el dinero no puede
 * perderse con ellos"). Solo revisa pagos con {@link Pago#idTransaccionPasarela()} registrado — sin
 * él no hay cómo consultar la API de Wompi, que busca por su id, no por la referencia propia. Un
 * pago sin ese id (el cliente cerró la pestaña antes de volver del checkout, y tampoco llegó el
 * webhook) queda fuera de este mecanismo, para seguimiento manual en el panel — la vista de
 * operación mínima de esta fase, no construida todavía.
 */
public final class ConciliarPagosPendientes {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final PasarelaDePagos pasarelaDePagos;
  private final Reloj reloj;
  private final Duration antiguedadMinima;

  public ConciliarPagosPendientes(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj,
      Duration antiguedadMinima) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.pasarelaDePagos = Objects.requireNonNull(pasarelaDePagos);
    this.reloj = Objects.requireNonNull(reloj);
    this.antiguedadMinima = Objects.requireNonNull(antiguedadMinima);
  }

  public ResultadoConciliacion ejecutar() {
    Instant ahora = reloj.ahora();
    List<Pago> pendientes =
        repositorioPagos.buscarPendientesParaConciliar(ahora.minus(antiguedadMinima));

    // Desde que hay dos pasarelas (adr/0048) esta consulta devuelve también los pagos de
    // Sistecrédito: la columna del id de transacción es la misma para las dos. Preguntarle a Wompi
    // por un `_id` de Sistecrédito no rompería nada —responde que no existe y el pago se salta—
    // pero gastaría una llamada por pago y por corrida, y sobre todo dejaría creyendo que esos
    // pagos están conciliados por alguien. Los concilia `ConciliarPagosSistecredito`.
    List<Pago> deWompi =
        pendientes.stream()
            .filter(pago -> pago.metodoPago().pasarela() == ProveedorDePago.WOMPI)
            .toList();

    int conciliados = 0;
    for (Pago pago : deWompi) {
      if (conciliar(pago, ahora)) {
        conciliados++;
      }
    }
    return new ResultadoConciliacion(deWompi.size(), conciliados, deWompi.size() - conciliados);
  }

  private boolean conciliar(Pago pago, Instant ahora) {
    String idTransaccionPasarela = pago.idTransaccionPasarela().orElseThrow();
    Optional<TransaccionDePasarela> transaccion =
        pasarelaDePagos.consultarTransaccion(idTransaccionPasarela);
    if (transaccion.isEmpty()) {
      return false;
    }
    String estadoWompi = transaccion.get().estado();
    EstadoPago nuevoEstado = EstadosWompi.aEstadoPago(estadoWompi);
    if (nuevoEstado == null) {
      return false;
    }
    EventoPago evento =
        new EventoPago(
            "conciliacion:" + idTransaccionPasarela + ":" + estadoWompi, nuevoEstado, ahora);
    ResultadoEventoDePago resultado =
        AplicadorDeResultadoDePago.aplicar(
            pago,
            evento,
            transaccion.get().medio(),
            "conciliacion-wompi",
            repositorioPagos,
            repositorioPedidos,
            repositorioInventario);
    return resultado == ResultadoEventoDePago.APLICADO
        || resultado == ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO;
  }
}
