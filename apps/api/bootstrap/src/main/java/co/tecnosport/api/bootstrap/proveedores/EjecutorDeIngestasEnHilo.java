package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.proveedores.ColaDeIngestasLlenaException;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Un hilo y una cola acotada, dentro del proceso.
 *
 * <p><b>Un solo hilo a propósito.</b> Dos lotes del mismo proveedor procesados a la vez podrían
 * registrar el mismo mensaje dos veces —los dos leen «no existe» antes de que ninguno escriba— y la
 * restricción única lo convertiría en un lote en error. Con un hilo el segundo lote ve lo que dejó
 * el primero. El precio es que un lote espera al anterior, y para el volumen de un chat de
 * proveedor eso son minutos.
 *
 * <p><b>El executor es privado, no un bean.</b> Boot registra su {@code applicationTaskExecutor}
 * solo cuando no hay otro {@code Executor} en el contexto; exponer este como bean lo reemplazaría
 * para todo lo que Spring MVC ejecuta aparte, con un hilo y una cola de veinte. Se cierra con el
 * contexto.
 *
 * <p><b>El latido va en otro hilo.</b> Mientras procesa un lote, un temporizador aparte avisa cada
 * {@code intervaloDelLatido} que sigue vivo con él, y para en cuanto lo suelta. Aparte porque entre
 * dos puntos de control del trabajo —subir dos mil fotos, una extracción con reintentos— pueden
 * pasar minutos, y un lote que no late lo cierra otra instancia ({@code
 * CerrarIngestasAbandonadas}).
 *
 * <p>En Cloud Run con CPU solo durante la petición, este hilo y el del latido solo avanzan mientras
 * hay tráfico, y el lote se da por abandonado aunque la instancia siga viva ({@code docs/07}). Por
 * eso la API lleva {@code cpu_siempre_asignada} en dev también desde el 11 de octubre de 2026.
 */
public final class EjecutorDeIngestasEnHilo implements EjecutorDeIngestas, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(EjecutorDeIngestasEnHilo.class);

  private final Function<UUID, LoteIngesta> procesar;
  private final Consumer<UUID> latir;
  private final Duration intervaloDelLatido;
  private final ThreadPoolExecutor executor;
  private final ScheduledExecutorService latidos;

  /**
   * @param procesar el trabajo entero de un lote; devuelve el lote como quedó
   * @param latir registra que el trabajador sigue vivo con ese lote, en su propia transacción
   */
  public EjecutorDeIngestasEnHilo(
      Function<UUID, LoteIngesta> procesar,
      int capacidadDeCola,
      Consumer<UUID> latir,
      Duration intervaloDelLatido) {
    this.procesar = Objects.requireNonNull(procesar);
    this.latir = Objects.requireNonNull(latir);
    this.intervaloDelLatido = Objects.requireNonNull(intervaloDelLatido);
    if (intervaloDelLatido.isNegative() || intervaloDelLatido.isZero()) {
      throw new IllegalArgumentException("El intervalo del latido tiene que ser positivo.");
    }
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
    this.latidos =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread hilo = new Thread(r, "latido-ingesta");
              hilo.setDaemon(true);
              return hilo;
            });
  }

  @Override
  public void encolar(UUID loteId) {
    try {
      executor.execute(() -> procesarConRegistro(loteId));
    } catch (RejectedExecutionException e) {
      throw new ColaDeIngestasLlenaException();
    }
  }

  private void procesarConRegistro(UUID loteId) {
    long intervalo = intervaloDelLatido.toMillis();
    ScheduledFuture<?> latido =
        latidos.scheduleWithFixedDelay(
            () -> latirSinRomper(loteId), intervalo, intervalo, TimeUnit.MILLISECONDS);
    try {
      LoteIngesta lote = procesar.apply(loteId);
      log.info(
          "Lote de ingesta {} del proveedor {}: {} {}",
          lote.id(),
          lote.proveedorId(),
          lote.estado(),
          lote.resumen().map(Object::toString).orElse(""));
    } catch (LoteNoEncontradoException e) {
      // Se detuvo en la cola y se eliminó antes de que el hilo llegara a él: no hay nada que hacer
      // y no es un fallo. Sin esto quedaba una traza de error en los registros por algo normal.
      log.info("El lote de ingesta {} se eliminó mientras esperaba en la cola.", loteId);
    } catch (RuntimeException | Error e) {
      // El lote ya quedó en ERROR con su motivo para el panel; aquí va la traza, que es lo que el
      // panel no enseña y lo que hace falta para arreglarlo. También los Error: un zip que no cabe
      // en memoria mata el hilo en silencio, y el pool lo repone pero nadie se entera.
      log.error("El lote de ingesta {} falló", loteId, e);
    } finally {
      latido.cancel(false);
    }
  }

  /**
   * Un latido que falla no puede tumbar los siguientes: una excepción que sale de aquí cancela la
   * tarea periódica en silencio, y el lote se daría por abandonado con el trabajador vivo.
   */
  private void latirSinRomper(UUID loteId) {
    try {
      latir.accept(loteId);
    } catch (RuntimeException e) {
      log.warn("No se pudo registrar el latido del lote de ingesta {}: {}", loteId, e.toString());
    }
  }

  @Override
  public void destroy() throws InterruptedException {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        log.warn(
            "La ingesta en curso no terminó en 30 segundos; el lote quedará abierto (en proceso,"
                + " en pausa o deteniéndose) y se cerrará con error cuando deje de latir"
                + " (CerrarIngestasAbandonadas).");
        executor.shutdownNow();
      }
    } finally {
      latidos.shutdownNow();
    }
  }
}
