package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.RecuperarIngestasAbandonadas;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada minuto, lo que se quedó sin nadie que lo mueva ({@link RecuperarIngestasAbandonadas}): el
 * lote cuyo trabajador dejó de latir vuelve a la cola, y el que lleva demasiado en ella sin que su
 * tarea llegue —un tramo que no se pudo encolar, una tarea perdida— se encola otra vez.
 *
 * <p>Es la red de seguridad, no el camino: el tramo siguiente lo encola quien termina el anterior;
 * si la tarea falla, Cloud Tasks la reintenta; y el tramo que encuentra el lote abandonado lo
 * retoma él mismo. Como las demás tareas programadas, solo corre mientras la instancia tiene CPU
 * ({@code docs/07}): en dev, mientras alguien usa el sitio o una tarea de la cola está en curso.
 * Con varias instancias corre en cada una y no importa: relee cada lote bloqueado antes de tocarlo,
 * y encolar dos veces el mismo lote no hace daño.
 */
@Component
public class TareaIngestasAbandonadas {

  private static final Logger log = LoggerFactory.getLogger(TareaIngestasAbandonadas.class);

  private final RecuperarIngestasAbandonadas recuperar;
  private final EjecutorDeIngestas ejecutor;
  private final EnTransaccionPropia enTransaccionPropia;

  public TareaIngestasAbandonadas(
      RecuperarIngestasAbandonadas recuperar,
      EjecutorDeIngestas ejecutor,
      EnTransaccionPropia enTransaccionPropia) {
    this.recuperar = Objects.requireNonNull(recuperar);
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
  }

  @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
  public void recuperarAbandonadas() {
    RecuperarIngestasAbandonadas.Resultado resultado =
        enTransaccionPropia.ejecutar(recuperar::ejecutar);
    if (resultado.retomados() > 0 || resultado.cerrados() > 0) {
      log.info(
          "Ingestas abandonadas: {} lote(s) devueltos a la cola, {} cerrados",
          resultado.retomados(),
          resultado.cerrados());
    }
    // Después de confirmar: el tramo que los tome tiene que encontrarlos como quedaron.
    for (UUID loteId : resultado.porEncolar()) {
      encolarSinRomper(ejecutor, loteId);
    }
  }

  /**
   * Un lote que no se pudo encolar sigue en la cola sin moverse: la vuelta siguiente lo intenta.
   */
  static void encolarSinRomper(EjecutorDeIngestas ejecutor, UUID loteId) {
    try {
      ejecutor.encolar(loteId);
    } catch (RuntimeException e) {
      log.warn("No se pudo encolar el lote de ingesta {}: {}", loteId, e.toString());
    }
  }
}
