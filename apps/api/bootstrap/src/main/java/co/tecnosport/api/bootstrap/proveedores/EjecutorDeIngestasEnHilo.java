package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.proveedores.ColaDeIngestasLlenaException;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.application.proveedores.ProcesarLoteDeIngesta;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
 * <p>En Cloud Run con CPU solo durante la petición, este hilo solo avanza mientras hay tráfico
 * ({@code docs/07}). En dev es aceptable; en producción la API lleva {@code cpu_siempre_asignada}.
 */
public final class EjecutorDeIngestasEnHilo implements EjecutorDeIngestas, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(EjecutorDeIngestasEnHilo.class);

  private final ProcesarLoteDeIngesta procesar;
  private final ThreadPoolExecutor executor;

  public EjecutorDeIngestasEnHilo(ProcesarLoteDeIngesta procesar, int capacidadDeCola) {
    this.procesar = Objects.requireNonNull(procesar);
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
      executor.execute(() -> procesarConRegistro(loteId));
    } catch (RejectedExecutionException e) {
      throw new ColaDeIngestasLlenaException();
    }
  }

  private void procesarConRegistro(UUID loteId) {
    try {
      LoteIngesta lote = procesar.ejecutar(loteId);
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
    }
  }

  @Override
  public void destroy() throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
      log.warn(
          "La ingesta en curso no terminó en 30 segundos; el lote quedará abierto (en proceso,"
              + " en pausa o deteniéndose) y el arranque siguiente lo cerrará con error"
              + " (ReanudadorDeIngestas).");
      executor.shutdownNow();
    }
  }
}
