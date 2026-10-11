package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.proveedores.MarcapasosDeIngesta;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Un hilo que late por cada lote que se está procesando, cada {@code intervalo}. Aparte del que
 * trabaja, porque entre dos puntos de control del trabajo pueden pasar minutos. Lo usan los dos
 * ejecutores: el tramo que llega por Cloud Tasks y el que corre en el hilo de la cola en memoria.
 */
public final class MarcapasosConTemporizador implements MarcapasosDeIngesta, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(MarcapasosConTemporizador.class);

  private final Consumer<UUID> latir;
  private final Duration intervalo;
  private final ScheduledExecutorService temporizador;

  /**
   * @param latir registra que el trabajador sigue vivo con ese lote, en su propia transacción
   */
  public MarcapasosConTemporizador(Consumer<UUID> latir, Duration intervalo) {
    this.latir = Objects.requireNonNull(latir);
    this.intervalo = Objects.requireNonNull(intervalo);
    if (intervalo.isNegative() || intervalo.isZero()) {
      throw new IllegalArgumentException("El intervalo del latido tiene que ser positivo.");
    }
    this.temporizador =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread hilo = new Thread(r, "latido-ingesta");
              hilo.setDaemon(true);
              return hilo;
            });
  }

  @Override
  public Latido latirPor(UUID loteId) {
    long milis = intervalo.toMillis();
    ScheduledFuture<?> tarea =
        temporizador.scheduleWithFixedDelay(
            () -> latirSinRomper(loteId), milis, milis, TimeUnit.MILLISECONDS);
    return () -> tarea.cancel(false);
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
  public void destroy() {
    temporizador.shutdownNow();
  }
}
