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
    // La misma contraprueba que `ProcesarNotificacionSistecredito` ya hacía por el otro camino, y
    // que aquí faltaba. Los dos caminos recorren el mismo `TransaccionSistecredito`, con el mismo
    // `valor()`; la diferencia era que por la notificación un crédito aprobado por menos de lo
    // pedido —un cupo tope, que es justo lo que hace un prestamista— no se aplicaba, y por aquí sí.
    // Y este es el camino que más se usa: el que existe precisamente para cuando el comprador
    // cierra la ventana en vez de pulsar "volver al comercio", que en móvil es lo normal.
    if (nuevoEstado == EstadoPago.APROBADO && !correspondeAEstePago(pago, transaccion.get())) {
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

  /**
   * Que la transacción aprobada sea la de este pago y por lo que este pago vale.
   *
   * <p>Copia deliberada de la guarda de {@code ProcesarNotificacionSistecredito}, con su misma
   * tolerancia a los campos ausentes: si la pasarela no manda el valor o la factura, no se bloquea
   * —negarse a aplicar un pago aprobado por un campo que quizá nunca venga sería peor—. Lo que no
   * se tolera es que vengan y no cuadren.
   *
   * <p>TODO (depende de una medición contra Sistecrédito): si {@code GetTransactionResponse}
   * devuelve siempre {@code invoice}, las dos comparaciones pasan a ser obligatorias y esta
   * tolerancia sobra. Lo medido el 23 de septiembre de 2026 es que **las notificaciones** llegan
   * sin él (docs/11-pagos-y-envios.md); de la respuesta de consulta no hay medición, y darla por
   * hecha en un camino que mueve dinero sería inventarse la API de un tercero.
   */
  private boolean correspondeAEstePago(Pago pago, TransaccionSistecredito verdad) {
    if (verdad.valor() != null && pago.monto().valor().longValueExact() != verdad.valor()) {
      return false;
    }
    return verdad.referencia() == null
        || verdad.referencia().isBlank()
        || verdad.referencia().equals(pago.referencia().valor());
  }
}
