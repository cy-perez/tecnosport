package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;

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
   * Toma la tarea si le toca —nunca corrió, o terminó hace al menos {@code intervalo}— y nadie la
   * tiene, o quien la tenía dejó vencer su arriendo. Devuelve {@code false} si no le toca o si la
   * tiene otro: en los dos casos no se corre.
   *
   * <p>Es una sola operación atómica: entre dos llamadas simultáneas con la misma tarea, una sola
   * recibe {@code true}.
   */
  boolean tomar(String nombre, Duration intervalo, Duration arriendo, Instant ahora);

  /**
   * Suelta el arriendo y anota cuándo terminó, haya salido bien o no. {@code fallo} es {@code null}
   * si salió bien.
   */
  void terminar(String nombre, Instant terminadaEn, String fallo);
}
