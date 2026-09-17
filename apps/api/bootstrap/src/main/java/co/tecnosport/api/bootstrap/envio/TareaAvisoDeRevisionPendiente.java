package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.envio.AvisarRevisionPendiente;
import co.tecnosport.api.application.envio.ResultadoVigilanciaRevision;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mira si algo lleva demasiado tiempo en la bandeja de revisión sin que nadie lo toque, y avisa.
 *
 * <p>Sin transacción alrededor del lote, por lo mismo que la resolución de emisiones: manda un
 * correo, y un correo enviado no se deshace con un rollback. Cada reclamo es una escritura atómica
 * propia, así que una vuelta que muera a la mitad deja avisado lo que alcanzó y nada a medias.
 *
 * <p>El registro sale en {@code warn} cuando de verdad se avisó: que haya salido un correo de estos
 * significa que algo con plata o con un paquete detenido llevaba más de un día esperando, y eso
 * merece aparecer en el registro aunque el correo se haya mandado bien.
 */
@Component
public class TareaAvisoDeRevisionPendiente {

  private static final Logger log = LoggerFactory.getLogger(TareaAvisoDeRevisionPendiente.class);

  private final AvisarRevisionPendiente avisarRevisionPendiente;

  public TareaAvisoDeRevisionPendiente(AvisarRevisionPendiente avisarRevisionPendiente) {
    this.avisarRevisionPendiente = Objects.requireNonNull(avisarRevisionPendiente);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.revision-envios.intervalo-minutos}",
      initialDelayString = "${tecnosport.revision-envios.retraso-inicial-minutos}",
      timeUnit = TimeUnit.MINUTES)
  public void vigilar() {
    ResultadoVigilanciaRevision resultado = avisarRevisionPendiente.ejecutar();
    if (resultado.avisadas() == 0) {
      return;
    }
    log.warn(
        "Revisión de envíos: {} llevan más del umbral sin revisar, se avisó de {}",
        resultado.vencidas(),
        resultado.avisadas());
  }
}
