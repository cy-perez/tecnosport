package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.AvisarSaldoBajo;
import co.tecnosport.api.application.envio.ResultadoVigilanciaSaldo;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Le pregunta a la plataforma cuánto crédito queda, y avisa si se está acabando.
 *
 * <p>Sin transacción, como las otras tareas de envío: no escribe nada: pregunta y, si hace falta,
 * manda un correo. Un correo enviado no se deshace con un rollback.
 *
 * <p><strong>Los cuatro desenlaces se registran distinto.</strong> "Hay plata" no se registra
 * —sería una línea cada doce horas diciendo que todo está bien—; "queda poco" va en {@code warn}
 * porque el despacho está a punto de detenerse; "no se pudo preguntar" va en {@code warn} también,
 * aunque no sea una alarma de dinero, porque repetido durante un día significa que esta vigilancia
 * no está vigilando nada; y "queda poco y encima no se pudo avisar" va en {@code error}, que es el
 * único de los cuatro que lo merece: el correo era toda la vigilancia, y esta línea es lo único que
 * queda de ella.
 */
@Component
public class TareaVigilanciaDeSaldo {

  private static final Logger log = LoggerFactory.getLogger(TareaVigilanciaDeSaldo.class);

  private final AvisarSaldoBajo avisarSaldoBajo;

  public TareaVigilanciaDeSaldo(AvisarSaldoBajo avisarSaldoBajo) {
    this.avisarSaldoBajo = Objects.requireNonNull(avisarSaldoBajo);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.saldo-envios.intervalo-minutos}",
      initialDelayString = "${tecnosport.saldo-envios.retraso-inicial-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void vigilar() {
    ResultadoVigilanciaSaldo resultado = avisarSaldoBajo.ejecutar();
    if (resultado.saldo().isEmpty()) {
      log.warn("No se pudo consultar el saldo de la plataforma de envios.");
      return;
    }
    if (resultado.avisoFallido()) {
      log.error(
          "Saldo de la plataforma de envios por debajo del umbral: {}, y el aviso no salio. Sin"
              + " credito no se emite ninguna guia.",
          resultado.saldo().orElseThrow().valor());
      return;
    }
    if (resultado.avisado()) {
      log.warn(
          "Saldo de la plataforma de envios por debajo del umbral: {}. Sin credito no se emite"
              + " ninguna guia.",
          resultado.saldo().orElseThrow().valor());
    }
  }
}
