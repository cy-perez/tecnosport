package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.ReanudarLotesDeIngesta;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, lo que el reinicio dejó en la cola vuelve a ella y lo que iba a medias se cierra.
 *
 * <p>Con {@code ApplicationReadyEvent} y no antes: para entonces el ejecutor existe y la base ya
 * migró. Con varias instancias corre en cada una y no pasa nada: encolar un lote {@code RECIBIDO}
 * dos veces lo procesa una, porque {@code ProcesarLoteDeIngesta} solo toma lo que sigue en la cola.
 */
@Component
public class ReanudadorDeIngestas {

  private static final Logger log = LoggerFactory.getLogger(ReanudadorDeIngestas.class);

  private final ReanudarLotesDeIngesta reanudar;
  private final EnTransaccionPropia enTransaccionPropia;

  public ReanudadorDeIngestas(
      ReanudarLotesDeIngesta reanudar, EnTransaccionPropia enTransaccionPropia) {
    this.reanudar = Objects.requireNonNull(reanudar);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void alArrancar() {
    ReanudarLotesDeIngesta.Resultado resultado = enTransaccionPropia.ejecutar(reanudar::ejecutar);
    if (resultado.reencolados() > 0 || resultado.fallidos() > 0) {
      log.info(
          "Ingestas al arrancar: {} lote(s) devueltos a la cola, {} cerrados con error",
          resultado.reencolados(),
          resultado.fallidos());
    }
  }
}
