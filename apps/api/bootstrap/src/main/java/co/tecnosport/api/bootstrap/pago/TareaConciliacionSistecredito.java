package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.pago.ConciliarPagosSistecredito;
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
 * Gemela de {@code TareaConciliacionWompi}, y por el mismo motivo: la conciliación corre dentro de
 * su propia transacción para que un fallo a mitad del lote no deje un {@code Pago} actualizado sin
 * su {@code Pedido}. Un lote fallido se reintenta en la siguiente corrida — el mecanismo es
 * idempotente por diseño.
 *
 * <p>Corre aunque el método esté apagado, y no pasa nada: sin pagos de Sistecrédito la consulta
 * devuelve una lista vacía y la tarea no llama a nadie.
 */
@Component
public class TareaConciliacionSistecredito {

  private static final Logger log = LoggerFactory.getLogger(TareaConciliacionSistecredito.class);

  private final ConciliarPagosSistecredito conciliarPagos;
  private final TransactionTemplate transaccion;

  public TareaConciliacionSistecredito(
      ConciliarPagosSistecredito conciliarPagos, PlatformTransactionManager transactionManager) {
    this.conciliarPagos = Objects.requireNonNull(conciliarPagos);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.sistecredito.conciliacion.intervalo-minutos}",
      initialDelayString = "${tecnosport.sistecredito.conciliacion.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void conciliar() {
    ResultadoConciliacion resultado = transaccion.execute(estado -> conciliarPagos.ejecutar());
    if (resultado.revisados() == 0) {
      return;
    }
    log.info(
        "Conciliación Sistecrédito: {} revisados, {} conciliados, {} sin novedad",
        resultado.revisados(),
        resultado.conciliados(),
        resultado.sinNovedad());
  }
}
