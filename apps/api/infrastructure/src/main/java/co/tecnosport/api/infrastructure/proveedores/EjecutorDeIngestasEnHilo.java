package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.proveedores.ColaDeIngestasLlenaException;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.application.proveedores.LoteOcupadoException;
import co.tecnosport.api.application.proveedores.TramoFallidoException;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * La cola de ingestas en memoria: un hilo y una cola acotada, dentro del proceso. Es la de local y
 * las pruebas; los ambientes desplegados usan Cloud Tasks ({@code ColaDeIngestasCloudTasks},
 * ADR-0080), porque en Cloud Run un hilo de fondo no tiene CPU entre peticiones y la instancia se
 * apaga con el trabajo a medias.
 *
 * <p>Cada elemento de la cola es un tramo ({@code AvanzarIngesta}), igual que una tarea de Cloud
 * Tasks: el lote que no cabe en un tramo vuelve a encolarse al final, detrás de los demás.
 *
 * <p><b>Un solo hilo a propósito</b>, como la cola de Cloud Tasks con un despacho a la vez: dos
 * tramos a la vez podrían registrar el mismo mensaje dos veces.
 *
 * <p><b>El executor es privado, no un bean.</b> Boot registra su {@code applicationTaskExecutor}
 * solo cuando no hay otro {@code Executor} en el contexto; exponer este como bean lo reemplazaría
 * para todo lo que Spring MVC ejecuta aparte, con un hilo y una cola de veinte. Se cierra con el
 * contexto.
 */
public final class EjecutorDeIngestasEnHilo implements EjecutorDeIngestas, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(EjecutorDeIngestasEnHilo.class);

  private final Function<UUID, LoteIngesta> avanzar;
  private final ThreadPoolExecutor executor;

  /**
   * @param avanzar un tramo del lote, con lo que encola después; devuelve el lote como quedó
   */
  public EjecutorDeIngestasEnHilo(Function<UUID, LoteIngesta> avanzar, int capacidadDeCola) {
    this.avanzar = Objects.requireNonNull(avanzar);
    this.executor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacidadDeCola),
            r -> {
              Thread hilo = new Thread(r, "ingesta-proveedores");
              hilo.setDaemon(true);
              return hilo;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  @Override
  public void encolar(UUID loteId) {
    try {
      executor.execute(() -> avanzarConRegistro(loteId));
    } catch (RejectedExecutionException e) {
      throw new ColaDeIngestasLlenaException();
    }
  }

  @Override
  public boolean sobreviveAUnReinicio() {
    return false;
  }

  private void avanzarConRegistro(UUID loteId) {
    try {
      LoteIngesta lote = avanzar.apply(loteId);
      log.info(
          "Tramo del lote de ingesta {} del proveedor {}: {} {}",
          lote.id(),
          lote.proveedorId(),
          lote.estado(),
          lote.resumen().map(Object::toString).orElse(""));
    } catch (LoteNoEncontradoException e) {
      // Se detuvo en la cola y se eliminó antes de que el hilo llegara a él: no hay nada que hacer
      // y no es un fallo. Sin esto quedaba una traza de error en los registros por algo normal.
      log.info("El lote de ingesta {} se eliminó mientras esperaba en la cola.", loteId);
    } catch (LoteOcupadoException e) {
      // Con una sola instancia en local no pasa; si pasa, el otro trabajador lo terminará.
      log.info("El lote de ingesta {} lo tiene otro trabajador.", loteId);
    } catch (TramoFallidoException e) {
      if (e.reintentar()) {
        // Lo que haría Cloud Tasks: reintentar. El tope de reintentos del lote corta el ciclo.
        log.warn("Tramo del lote de ingesta {} falló; vuelve a la cola", loteId, e.getCause());
        try {
          encolar(loteId);
        } catch (RuntimeException sinLugar) {
          log.warn(
              "No se pudo volver a encolar el lote de ingesta {}: {}", loteId, sinLugar.toString());
        }
      } else {
        // El lote ya quedó en ERROR con su motivo para el panel; aquí va la traza.
        log.error("El lote de ingesta {} falló", loteId, e.getCause());
      }
    } catch (RuntimeException | Error e) {
      // Lo que no es del tramo —encolar el siguiente, por ejemplo—. También los Error: un hilo
      // que muere en silencio lo repone el pool pero nadie se entera. El lote, si quedó en la
      // cola, lo recoge la tarea de los estancados.
      log.error("El lote de ingesta {} falló", loteId, e);
    }
  }

  @Override
  public void destroy() throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
      log.warn(
          "La ingesta en curso no terminó en 30 segundos; el lote quedará en proceso y se"
              + " retomará cuando deje de latir (RecuperarIngestasAbandonadas).");
      executor.shutdownNow();
    }
  }
}
