package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.ReanudarLotesDeIngesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, lo abandonado vuelve a la cola, y la cola en memoria se rehace ({@link
 * ReanudarLotesDeIngesta}).
 *
 * <p>Con {@code ApplicationReadyEvent} y no antes: para entonces el ejecutor existe y la base ya
 * migró. Con varias instancias corre en cada una. Lo que está en manos de un trabajador solo se
 * toca si dejó de latir: hasta el 11 de octubre de 2026 este párrafo decía que no pasaba nada, y
 * cada instancia que arrancaba cerraba con error lo que su vecina estaba procesando.
 */
@Component
public class ReanudadorDeIngestas {

  private static final Logger log = LoggerFactory.getLogger(ReanudadorDeIngestas.class);

  private final ReanudarLotesDeIngesta reanudar;
  private final EjecutorDeIngestas ejecutor;
  private final EnTransaccionPropia enTransaccionPropia;

  public ReanudadorDeIngestas(
      ReanudarLotesDeIngesta reanudar,
      EjecutorDeIngestas ejecutor,
      EnTransaccionPropia enTransaccionPropia) {
    this.reanudar = Objects.requireNonNull(reanudar);
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void alArrancar() {
    ReanudarLotesDeIngesta.Resultado resultado = enTransaccionPropia.ejecutar(reanudar::ejecutar);
    if (!resultado.porEncolar().isEmpty() || resultado.cerrados() > 0) {
      log.info(
          "Ingestas al arrancar: {} lote(s) a la cola ({} retomados), {} cerrados",
          resultado.porEncolar().size(),
          resultado.retomados(),
          resultado.cerrados());
    }
    for (UUID loteId : resultado.porEncolar()) {
      TareaIngestasAbandonadas.encolarSinRomper(ejecutor, loteId);
    }
  }
}
