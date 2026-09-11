package co.tecnosport.api.infrastructure.envio;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Skydropx acepta hasta 2 peticiones por segundo. Una cotización son varias llamadas seguidas —el
 * token, la creación y N sondeos—, así que el límite se alcanza sin hacer nada raro.
 *
 * <p>El reloj y la pausa entran por constructor para poder probar esto sin dormir el hilo de
 * verdad: una prueba que duerme medio segundo por caso deja de correrse.
 */
final class LimitadorDePeticiones {

  /** Extraída para que las pruebas observen cuánto se habría dormido, en vez de dormirlo. */
  interface Pausador {
    void pausar(Duration duracion) throws InterruptedException;
  }

  private final long intervaloMinimoNanos;
  private final LongSupplier nanos;
  private final Pausador pausador;

  private Long ultimaPeticionNanos;

  LimitadorDePeticiones(Duration intervaloMinimo, LongSupplier nanos, Pausador pausador) {
    Objects.requireNonNull(intervaloMinimo, "El intervalo mínimo no puede ser nulo.");
    if (intervaloMinimo.isNegative()) {
      throw new IllegalArgumentException("El intervalo mínimo no puede ser negativo.");
    }
    this.intervaloMinimoNanos = intervaloMinimo.toNanos();
    this.nanos = Objects.requireNonNull(nanos);
    this.pausador = Objects.requireNonNull(pausador);
  }

  static LimitadorDePeticiones deSegundo(int peticionesPorSegundo) {
    if (peticionesPorSegundo <= 0) {
      throw new IllegalArgumentException(
          "Las peticiones por segundo deben ser mayores que cero: " + peticionesPorSegundo);
    }
    return new LimitadorDePeticiones(
        Duration.ofSeconds(1).dividedBy(peticionesPorSegundo), System::nanoTime, Thread::sleep);
  }

  /** Bloquea lo justo para que entre esta petición y la anterior pase el intervalo mínimo. */
  synchronized void esperarTurno() throws InterruptedException {
    long ahora = nanos.getAsLong();
    if (ultimaPeticionNanos != null) {
      long transcurrido = ahora - ultimaPeticionNanos;
      if (transcurrido < intervaloMinimoNanos) {
        pausador.pausar(Duration.ofNanos(intervaloMinimoNanos - transcurrido));
        ahora = nanos.getAsLong();
      }
    }
    ultimaPeticionNanos = ahora;
  }
}
