package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.ConciliarEnvios;
import co.tecnosport.api.application.envio.ResultadoConciliacionEnvios;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tercera tarea programada del proyecto, con el mismo patrón que {@code TareaConciliacionWompi}:
 * corre dentro de su propia transacción, y un lote fallido simplemente se reintenta en la siguiente
 * vuelta porque el mecanismo es idempotente por diseño (adr/0022).
 *
 * <p>Existe porque los webhooks se pierden. Un paquete entregado hace cinco días con el pedido
 * todavía en {@code DESPACHADO} es un retracto que empieza a correr sin que el sistema lo sepa, y
 * el comprador se entera antes que el negocio.
 *
 * <p>Hoy no encuentra nada: el consultor todavía no sabe preguntarle a Skydropx, así que devuelve
 * lista vacía y la tarea registra que no hubo novedad. Corre igual, para que el día que se conecte
 * no haya que descubrir que el cableado tenía un error.
 */
@Component
public class TareaConciliacionEnvios {

  private static final Logger log = LoggerFactory.getLogger(TareaConciliacionEnvios.class);

  private final ConciliarEnvios conciliarEnvios;
  private final TransactionTemplate transaccion;

  public TareaConciliacionEnvios(
      ConciliarEnvios conciliarEnvios, PlatformTransactionManager transactionManager) {
    this.conciliarEnvios = Objects.requireNonNull(conciliarEnvios);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.skydropx.seguimiento.intervalo-minutos}",
      initialDelayString = "${tecnosport.skydropx.seguimiento.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void conciliar() {
    ResultadoConciliacionEnvios resultado =
        transaccion.execute(estado -> conciliarEnvios.ejecutar());
    log.info(
        "Conciliación de envíos: {} revisados, {} con eventos nuevos, {} sin novedad",
        resultado.revisados(),
        resultado.conEventosNuevos(),
        resultado.sinNovedad());
  }
}
