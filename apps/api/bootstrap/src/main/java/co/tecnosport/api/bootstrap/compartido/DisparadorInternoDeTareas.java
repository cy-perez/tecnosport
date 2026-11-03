package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.EjecutarTareasVencidas;
import co.tecnosport.api.application.compartido.ResultadoDeLaVuelta;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El tick dentro de la aplicación, para local y las pruebas (ADR-0104): lo mismo que hace Cloud
 * Scheduler en los ambientes desplegados, cada minuto. Ahí la API tiene CPU solo durante una
 * petición, así que este {@code @Scheduled} no correría, y con {@code TAREAS_DISPARADOR=externo}
 * tampoco existe.
 *
 * <p>Es el único {@code @Scheduled} del backend, y debería seguir siéndolo: una tarea nueva se
 * registra en {@code CatalogoDeTareasProgramadas}.
 */
@Component
@ConditionalOnProperty(
    name = "tecnosport.tareas.disparador",
    havingValue = "interno",
    matchIfMissing = true)
public class DisparadorInternoDeTareas {

  private static final Logger log = LoggerFactory.getLogger(DisparadorInternoDeTareas.class);

  private final EjecutarTareasVencidas ejecutarTareasVencidas;

  public DisparadorInternoDeTareas(EjecutarTareasVencidas ejecutarTareasVencidas) {
    this.ejecutarTareasVencidas = Objects.requireNonNull(ejecutarTareasVencidas);
  }

  @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
  public void tick() {
    ResultadoDeLaVuelta resultado = ejecutarTareasVencidas.ejecutar();
    for (ResultadoDeLaVuelta.Fallo fallo : resultado.fallidas()) {
      log.error(
          "Tick: la tarea {} falló y se reintenta cuando pase su intervalo: {}",
          fallo.tarea(),
          fallo.motivo());
    }
  }
}
