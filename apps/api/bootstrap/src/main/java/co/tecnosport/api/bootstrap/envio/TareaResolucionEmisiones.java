package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.ResolverEmisionesEnCurso;
import co.tecnosport.api.application.envio.ResultadoResolucionEmisiones;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cuarta tarea programada del proyecto, y <strong>la primera que no envuelve el lote en una
 * transacción</strong>. Las otras tres pueden: su trabajo es idempotente y reintentar la vuelta
 * entera no cuesta nada. Aquí no, por dos motivos medidos — despachar manda un correo al comprador,
 * y un correo enviado no se deshace con un rollback: se reenvía en la vuelta siguiente; y una
 * emisión que no se deja resolver bloquearía a todas las demás para siempre, porque el lote se lee
 * de la más vieja a la más nueva y la ofensora vuelve a ser la primera. Cada emisión va en su
 * propia transacción, dentro del caso de uso (adr/0033).
 *
 * <p><strong>Corre mucho más seguido que las otras tres, y con razón.</strong> Las demás vigilan
 * cosas que pasan en días; esta espera una respuesta que llega en segundos o minutos, y detrás de
 * ella hay un pedido pagado que no sale del almacén hasta que alguien sepa el número de guía. Un
 * minuto de intervalo contra un lote acotado son pocas llamadas: solo hay emisiones en curso cuando
 * alguien acaba de pulsar el botón.
 *
 * <p>El registro sale en {@code warn} cuando hay algo que una persona tenga que mirar —una parcial,
 * una que no se dejó resolver, una que se quedó sin respuesta—, y en esos casos escribe además los
 * motivos uno por uno. Callarlos era el problema de fondo del lote transaccional: el síntoma de que
 * la tarea estaba atascada era una traza, y nada más.
 */
@Component
public class TareaResolucionEmisiones {

  private static final Logger log = LoggerFactory.getLogger(TareaResolucionEmisiones.class);

  private final ResolverEmisionesEnCurso resolverEmisiones;

  public TareaResolucionEmisiones(ResolverEmisionesEnCurso resolverEmisiones) {
    this.resolverEmisiones = Objects.requireNonNull(resolverEmisiones);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.skydropx.emision.intervalo-segundos}",
      initialDelayString = "${tecnosport.skydropx.emision.intervalo-segundos}",
      timeUnit = TimeUnit.SECONDS)
  public void resolver() {
    ResultadoResolucionEmisiones resultado = resolverEmisiones.ejecutar();
    if (resultado.revisadas() == 0 && resultado.abandonadas() == 0) {
      return;
    }
    String linea =
        "Resolución de emisiones: {} revisadas, {} despachadas, {} fallidas, {} parciales,"
            + " {} siguen en curso, {} sin respuesta del proveedor, {} con error, {} abandonadas";
    Object[] datos = {
      resultado.revisadas(),
      resultado.despachadas(),
      resultado.fallidas(),
      resultado.parciales(),
      resultado.siguenEnCurso(),
      resultado.sinRespuesta(),
      resultado.conError(),
      resultado.abandonadas()
    };
    if (resultado.exigeOjoHumano()) {
      log.warn(linea, datos);
      // Los motivos aparte y solo cuando los hay: una emisión que no se deja resolver deja de ser
      // invisible, que era el problema de fondo del lote transaccional.
      resultado.errores().forEach(error -> log.warn("Emisión que no se pudo resolver: {}", error));
      return;
    }
    log.info(linea, datos);
  }
}
