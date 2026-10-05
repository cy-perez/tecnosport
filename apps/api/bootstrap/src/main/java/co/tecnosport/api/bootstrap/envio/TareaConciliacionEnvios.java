package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.ConciliarEnvios;
import co.tecnosport.api.application.envio.ResultadoConciliacionEnvios;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tercera tarea programada del proyecto. <b>No abre una transacción para el lote</b>, y la abría
 * hasta el 4 de octubre de 2026: cada guía abre la suya dentro de {@code ConciliarEnvios}, porque
 * una sola guía que reventaba revertía la corrida entera en cada vuelta, siempre la misma. El
 * mecanismo sigue siendo idempotente (adr/0022): lo que falle se reintenta en la vuelta siguiente.
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

  public TareaConciliacionEnvios(ConciliarEnvios conciliarEnvios) {
    this.conciliarEnvios = Objects.requireNonNull(conciliarEnvios);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.skydropx.seguimiento.intervalo-minutos}",
      initialDelayString = "${tecnosport.skydropx.seguimiento.intervalo-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void conciliar() {
    ResultadoConciliacionEnvios resultado = conciliarEnvios.ejecutar();
    log.info(
        "Conciliación de envíos: {} revisados, {} con eventos nuevos, {} sin novedad,"
            + " {} guías sin código de transportadora",
        resultado.revisados(),
        resultado.conEventosNuevos(),
        resultado.sinNovedad(),
        resultado.guiasSinCodigo());
    if (resultado.guiasConError() > 0) {
      log.error(
          "Conciliación de envíos: {} guías fallaron y se reintentan en la vuelta siguiente: {}",
          resultado.guiasConError(),
          resultado.errores());
    }
  }
}
