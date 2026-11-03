package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.EjecutarTareasVencidas;
import co.tecnosport.api.application.compartido.ResultadoDelTick;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que llama Cloud Scheduler en cada tick (ADR-0104). No es del panel ni del público: lo protege
 * {@link FiltroTareasProgramadas}, y no sale en el contrato publicado.
 *
 * <p><b>Responde {@code 200} aunque alguna tarea falle.</b> Un {@code 5xx} haría que Cloud
 * Scheduler reintentara el tick entero, y las que salieron bien no tienen nada que repetir; la que
 * falló ya quedó anotada y vuelve a intentarse cuando pase su intervalo. El fallo se ve aquí, en el
 * registro en {@code error}, y en {@code ejecucion_de_tarea.fallo}.
 *
 * <p>La petición dura lo que duren las tareas que tocaban, y eso es lo que las hace correr:
 * mientras una petición está abierta, la instancia tiene CPU.
 */
@RestController
@RequestMapping("/api/v1/internal/tareas")
public class TareasInternasControlador {

  private static final Logger log = LoggerFactory.getLogger(TareasInternasControlador.class);

  private final EjecutarTareasVencidas ejecutarTareasVencidas;

  public TareasInternasControlador(EjecutarTareasVencidas ejecutarTareasVencidas) {
    this.ejecutarTareasVencidas = Objects.requireNonNull(ejecutarTareasVencidas);
  }

  @PostMapping("/tick")
  public ResponseEntity<ResultadoDelTick> tick() {
    ResultadoDelTick resultado = ejecutarTareasVencidas.ejecutar();
    if (!resultado.corridas().isEmpty()) {
      log.info("Tick: corrieron {}", resultado.corridas());
    }
    for (ResultadoDelTick.Fallo fallo : resultado.fallidas()) {
      log.error(
          "Tick: la tarea {} falló y se reintenta cuando pase su intervalo: {}",
          fallo.tarea(),
          fallo.motivo());
    }
    return ResponseEntity.ok(resultado);
  }
}
