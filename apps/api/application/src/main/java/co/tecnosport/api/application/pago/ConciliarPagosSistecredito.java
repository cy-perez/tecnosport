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
 * Lo mismo que {@link ConciliarPagosPendientes} hace con Wompi, para Sistecrédito ({@code
 * adr/0048}). Gemelo y no una generalización de aquel: comparten la forma —buscar pendientes,
 * consultar, aplicar— y no comparten nada de lo que importa, porque cada pasarela tiene su puerto,
 * su traducción de estados y su nombre de actor en el historial.
 *
 * <p>Aquí hace más falta que en Wompi, y por dos motivos que la guía {@code G-ALI-12} deja
 * escritos:
 *
 * <ul>
 *   <li>Si el comprador cierra la ventana en vez de pulsar "volver al comercio", la confirmación
 *       queda "en manos de la pasarela" y puede tardar hasta tres minutos.
 *   <li>La notificación no viene firmada, así que {@code ProcesarNotificacionSistecredito} no
 *       aplica nada cuando no consigue verificarla. Esos pagos quedan esperando exactamente esto.
 * </ul>
 */
public final class ConciliarPagosSistecredito {

  private final RepositorioPagos repositorioPagos;
  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final PasarelaSistecredito pasarela;
  private final Reloj reloj;
  private final Duration antiguedadMinima;

  public ConciliarPagosSistecredito(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaSistecredito pasarela,
      Reloj reloj,
      Duration antiguedadMinima) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.pasarela = Objects.requireNonNull(pasarela);
    this.reloj = Objects.requireNonNull(reloj);
    this.antiguedadMinima = Objects.requireNonNull(antiguedadMinima);
  }

  public ResultadoConciliacion ejecutar() {
    Instant ahora = reloj.ahora();
    List<Pago> deSistecredito =
        repositorioPagos.buscarPendientesParaConciliar(ahora.minus(antiguedadMinima)).stream()
            .filter(pago -> pago.metodoPago().pasarela() == ProveedorDePago.SISTECREDITO)
            .toList();

    int conciliados = 0;
    for (Pago pago : deSistecredito) {
      if (conciliar(pago, ahora)) {
        conciliados++;
      }
    }
    return new ResultadoConciliacion(
        deSistecredito.size(), conciliados, deSistecredito.size() - conciliados);
  }

  private boolean conciliar(Pago pago, Instant ahora) {
    String idTransaccion = pago.idTransaccionPasarela().orElseThrow();
    Optional<TransaccionSistecredito> transaccion = pasarela.consultar(idTransaccion);
    if (transaccion.isEmpty()) {
      return false;
    }
    String estadoPasarela = transaccion.get().estado();
    EstadoPago nuevoEstado = EstadosSistecredito.aEstadoPago(estadoPasarela);
    if (nuevoEstado == null) {
      // Sigue en vuelo. No es un fallo: la próxima corrida lo vuelve a mirar.
      return false;
    }
    // Mismo id de evento que usaría la notificación para ese estado, a propósito: si la
    // notificación llega tarde, después de que la conciliación ya resolvió el pago, el agregado la
    // reconoce como repetida en vez de intentar aplicarla sobre un estado final.
    EventoPago evento =
        new EventoPago(transaccion.get().id() + ":" + estadoPasarela, nuevoEstado, ahora);
    ResultadoEventoDePago resultado =
        AplicadorDeResultadoDePago.aplicar(
            pago,
            evento,
            "SISTECREDITO",
            "conciliacion-sistecredito",
            repositorioPagos,
            repositorioPedidos,
            repositorioInventario);
    return resultado == ResultadoEventoDePago.APLICADO
        || resultado == ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO;
  }
}
