package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.CerrarIngestasAbandonadas;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cierra cada minuto los lotes de ingesta que dejaron de latir.
 *
 * <p>Sin esto, lo que una instancia deja en las manos al apagarse —un despliegue, la escala a cero—
 * esperaba al arranque siguiente, que en producción, con una instancia siempre encendida, puede
 * tardar días; y el panel no deja eliminar un lote abierto. Con varias instancias corre en cada una
 * y no importa: relee cada lote bloqueado antes de cerrarlo, y cerrar uno ya cerrado no hace nada.
 *
 * <p>Sin bandera ni intervalo configurable, a diferencia de las otras tareas: no llama a nadie de
 * fuera ni cambia nada que el negocio vea mientras haya trabajadores vivos, y el minuto va con la
 * tolerancia del latido ({@code ConfiguracionProveedores}).
 */
@Component
public class TareaIngestasAbandonadas {

  private static final Logger log = LoggerFactory.getLogger(TareaIngestasAbandonadas.class);

  private final CerrarIngestasAbandonadas cerrar;
  private final EnTransaccionPropia enTransaccionPropia;

  public TareaIngestasAbandonadas(
      CerrarIngestasAbandonadas cerrar, EnTransaccionPropia enTransaccionPropia) {
    this.cerrar = Objects.requireNonNull(cerrar);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
  }

  @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
  public void cerrarAbandonadas() {
    int cerrados = enTransaccionPropia.ejecutar(cerrar::ejecutar);
    if (cerrados > 0) {
      log.info("Ingestas abandonadas: {} lote(s) cerrados con error por dejar de latir", cerrados);
    }
  }
}
