package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuándo corrió cada {@link TareaProgramada}, guardado fuera de la aplicación para que un reinicio
 * no borre la cuenta (ADR-0104).
 *
 * <p>Tomar una tarea es un arriendo y no un bloqueo: la implementación la marca como en curso en
 * una sola sentencia, confirmada por su cuenta, y no deja ninguna transacción abierta mientras la
 * tarea corre. Si la sostuviera, los casos de uso de la tarea se unirían a ella y cada uno dejaría
 * de confirmar por su lado.
 */
public interface BitacoraDeTareas {

  /**
   * Toma la tarea para {@code quien} si le toca —se tomó por última vez hace al menos {@code
   * intervalo}— y nadie la tiene, o quien la tenía dejó vencer su arriendo. Devuelve {@code false}
   * si no le toca, si la tiene otro, o si es la primera vez que se ve: entonces solo la anota, como
   * si acabara de tomarse, y corre cuando pase su intervalo.
   *
   * <p>El intervalo se cuenta desde que se tomó y no desde que terminó: si no, una tarea del mismo
   * intervalo que el tick se saltaría uno de cada dos.
   *
   * <p>Es una sola operación atómica: entre dos llamadas simultáneas con la misma tarea, una sola
   * recibe {@code true}.
   */
  boolean tomar(String nombre, Duration intervalo, Duration arriendo, UUID quien, Instant ahora);

  /**
   * Suelta el arriendo y anota cuándo terminó, haya salido bien o no. {@code fallo} es {@code null}
   * si salió bien.
   *
   * <p>Solo si el arriendo sigue siendo de {@code quien}. Si venció y otro tomó la tarea, esta
   * vuelta ya no es la dueña, y soltarlo le quitaría la tarea al que la está corriendo.
   */
  void terminar(String nombre, UUID quien, Instant terminadaEn, String fallo);
}
