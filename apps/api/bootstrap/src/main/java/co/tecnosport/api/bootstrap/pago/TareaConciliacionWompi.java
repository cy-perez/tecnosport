package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.pago.ConciliarPagosPendientes;
import co.tecnosport.api.application.pago.ResultadoConciliacion;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre {@code ConciliarPagosPendientes}, que aplica <b>cada pago en su propia transacción</b> —el
 * pago y su pedido juntos, como antes— y consulta a Wompi fuera de cualquiera. Hasta el 4 de
 * octubre de 2026 la tarea envolvía el lote entero: un pago que reventaba revertía lo ya
 * conciliado, y la conexión de la base quedaba retenida mientras Wompi contestaba. Lo que falle se
 * reintenta en la corrida siguiente: el mecanismo es idempotente por diseño.
 */
@Component
public class TareaConciliacionWompi {

  private static final Logger log = LoggerFactory.getLogger(TareaConciliacionWompi.class);

  private final ConciliarPagosPendientes conciliarPagosPendientes;

  public TareaConciliacionWompi(ConciliarPagosPendientes conciliarPagosPendientes) {
    this.conciliarPagosPendientes = Objects.requireNonNull(conciliarPagosPendientes);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.wompi.conciliacion.intervalo-minutos}",
      initialDelayString = "${tecnosport.wompi.conciliacion.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void conciliar() {
    ResultadoConciliacion resultado = conciliarPagosPendientes.ejecutar();
    log.info(
        "Conciliación Wompi: {} revisados, {} conciliados, {} sin novedad",
        resultado.revisados(),
        resultado.conciliados(),
        resultado.sinNovedad());
    if (!resultado.errores().isEmpty()) {
      log.error(
          "Conciliación: {} pagos fallaron y se reintentan en la vuelta siguiente: {}",
          resultado.errores().size(),
          resultado.errores());
    }
  }
}
