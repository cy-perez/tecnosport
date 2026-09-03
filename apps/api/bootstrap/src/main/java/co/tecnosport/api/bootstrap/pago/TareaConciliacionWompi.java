package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.pago.ConciliarPagosPendientes;
import co.tecnosport.api.application.pago.ResultadoConciliacion;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Corre {@code ConciliarPagosPendientes} dentro de su propia transacción, igual que {@code
 * PagoControlador} con {@code CrearIntentoDePago}: sin eso, un fallo a mitad del lote podría dejar
 * un {@code Pago} actualizado sin su {@code Pedido}, o un evento a medio guardar
 * (docs/11-pagos-y-envios.md: "el dinero no puede perderse"). Un lote fallido simplemente se
 * reintenta en la siguiente corrida — el mecanismo es idempotente por diseño.
 */
@Component
public class TareaConciliacionWompi {

  private static final Logger log = LoggerFactory.getLogger(TareaConciliacionWompi.class);

  private final ConciliarPagosPendientes conciliarPagosPendientes;
  private final TransactionTemplate transaccion;

  public TareaConciliacionWompi(
      ConciliarPagosPendientes conciliarPagosPendientes,
      PlatformTransactionManager transactionManager) {
    this.conciliarPagosPendientes = Objects.requireNonNull(conciliarPagosPendientes);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.wompi.conciliacion.intervalo-minutos}",
      initialDelayString = "${tecnosport.wompi.conciliacion.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void conciliar() {
    ResultadoConciliacion resultado =
        transaccion.execute(estado -> conciliarPagosPendientes.ejecutar());
    log.info(
        "Conciliación Wompi: {} revisados, {} conciliados, {} sin novedad",
        resultado.revisados(),
        resultado.conciliados(),
        resultado.sinNovedad());
  }
}
