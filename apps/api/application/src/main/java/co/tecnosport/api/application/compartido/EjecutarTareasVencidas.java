package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Un tick: corre, una tras otra, cada {@link TareaProgramada} a la que le toca (ADR-0104).
 *
 * <p>Lo llama Cloud Scheduler en los ambientes desplegados, y un {@code @Scheduled} de un minuto en
 * local. Las tareas corren dentro de la llamada porque fuera de ella no hay CPU.
 *
 * <p><b>Una tarea que falla no detiene a las demás y queda anotada como terminada</b>: espera su
 * intervalo para volver a intentarlo, que es lo que hacía {@code @Scheduled} con una excepción.
 * Cada tarea ya está hecha para eso —lo que no se concilió en una vuelta se concilia en la
 * siguiente—, y reintentarla en cada tick a una tarea que falla por un tercero caído solo
 * multiplicaría las llamadas a ese tercero.
 *
 * <p>Solo se atrapa {@link RuntimeException}. Un {@code Error} —sin memoria, por ejemplo— deja la
 * tarea con su arriendo puesto, y el arriendo vence: es el caso de la instancia que muere a la
 * mitad, y se resuelve igual.
 */
public final class EjecutarTareasVencidas {

  /**
   * El motivo de un fallo se guarda y se devuelve; una traza entera no cabe en ninguno de los dos.
   */
  static final int LARGO_MAXIMO_DEL_MOTIVO = 1000;

  private final List<TareaProgramada> tareas;
  private final BitacoraDeTareas bitacora;
  private final Reloj reloj;
  private final Duration arriendo;

  /**
   * @param arriendo cuánto tiempo es de quien la tomó una tarea que no termina. Tiene que ser mayor
   *     que lo que puede durar la tarea más larga, o una vecina la tomaría mientras todavía corre;
   *     con el tiempo máximo de una petición de Cloud Run alcanza, porque más que eso no dura nada.
   */
  public EjecutarTareasVencidas(
      List<TareaProgramada> tareas, BitacoraDeTareas bitacora, Reloj reloj, Duration arriendo) {
    this.tareas = List.copyOf(tareas);
    this.bitacora = Objects.requireNonNull(bitacora);
    this.reloj = Objects.requireNonNull(reloj);
    this.arriendo = Objects.requireNonNull(arriendo);
    if (arriendo.isNegative() || arriendo.isZero()) {
      throw new IllegalArgumentException("El arriendo debe ser mayor que cero.");
    }
    Set<String> nombres = new HashSet<>();
    for (TareaProgramada tarea : this.tareas) {
      if (!nombres.add(tarea.nombre())) {
        // Dos tareas con el mismo nombre comparten fila: la que corre primero le roba el turno a la
        // otra en cada tick, y la segunda no corre nunca sin que nada lo avise.
        throw new IllegalArgumentException("Dos tareas se llaman igual: " + tarea.nombre());
      }
    }
  }

  public ResultadoDelTick ejecutar() {
    List<String> corridas = new ArrayList<>();
    List<ResultadoDelTick.Fallo> fallidas = new ArrayList<>();
    int omitidas = 0;
    for (TareaProgramada tarea : tareas) {
      if (!bitacora.tomar(tarea.nombre(), tarea.intervalo(), arriendo, reloj.ahora())) {
        omitidas++;
        continue;
      }
      try {
        tarea.ejecutar();
        bitacora.terminar(tarea.nombre(), reloj.ahora(), null);
        corridas.add(tarea.nombre());
      } catch (RuntimeException e) {
        String motivo = motivo(e);
        bitacora.terminar(tarea.nombre(), reloj.ahora(), motivo);
        fallidas.add(new ResultadoDelTick.Fallo(tarea.nombre(), motivo));
      }
    }
    return new ResultadoDelTick(corridas, fallidas, omitidas);
  }

  private static String motivo(RuntimeException e) {
    String motivo = e.toString();
    return motivo.length() <= LARGO_MAXIMO_DEL_MOTIVO
        ? motivo
        : motivo.substring(0, LARGO_MAXIMO_DEL_MOTIVO);
  }
}
