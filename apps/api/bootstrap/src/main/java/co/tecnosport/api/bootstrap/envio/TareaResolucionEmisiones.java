package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.ResolverEmisionesEnCurso;
import co.tecnosport.api.application.envio.ResultadoResolucionEmisiones;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cuarta tarea programada del proyecto, con el mismo patrón que {@code TareaConciliacionEnvios}:
 * corre dentro de su propia transacción y un lote fallido se reintenta en la vuelta siguiente,
 * porque el mecanismo es idempotente por diseño (adr/0033).
 *
 * <p><strong>Corre mucho más seguido que las otras tres, y con razón.</strong> Las demás vigilan
 * cosas que pasan en días; esta espera una respuesta que llega en segundos o minutos, y detrás de
 * ella hay un pedido pagado que no sale del almacén hasta que alguien sepa el número de guía. Un
 * minuto de intervalo contra un lote acotado son pocas llamadas: solo hay emisiones en curso cuando
 * alguien acaba de pulsar el botón.
 *
 * <p>El registro sale en {@code warn} cuando hay emisiones parciales, y no en {@code info}: una
 * parcial es una guía pagada y viva que nadie va a usar hasta que una persona la mire.
 */
@Component
public class TareaResolucionEmisiones {

  private static final Logger log = LoggerFactory.getLogger(TareaResolucionEmisiones.class);

  private final ResolverEmisionesEnCurso resolverEmisiones;
  private final TransactionTemplate transaccion;

  public TareaResolucionEmisiones(
      ResolverEmisionesEnCurso resolverEmisiones, PlatformTransactionManager transactionManager) {
    this.resolverEmisiones = Objects.requireNonNull(resolverEmisiones);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.skydropx.emision.intervalo-segundos}",
      initialDelayString = "${tecnosport.skydropx.emision.intervalo-segundos}",
      timeUnit = TimeUnit.SECONDS)
  public void resolver() {
    ResultadoResolucionEmisiones resultado =
        transaccion.execute(estado -> resolverEmisiones.ejecutar());
    if (resultado.revisadas() == 0) {
      return;
    }
    String linea =
        "Resolución de emisiones: {} revisadas, {} despachadas, {} fallidas, {} parciales,"
            + " {} siguen en curso, {} sin respuesta del proveedor";
    if (resultado.parciales() > 0) {
      log.warn(
          linea,
          resultado.revisadas(),
          resultado.despachadas(),
          resultado.fallidas(),
          resultado.parciales(),
          resultado.siguenEnCurso(),
          resultado.sinRespuesta());
      return;
    }
    log.info(
        linea,
        resultado.revisadas(),
        resultado.despachadas(),
        resultado.fallidas(),
        resultado.parciales(),
        resultado.siguenEnCurso(),
        resultado.sinRespuesta());
  }
}
