package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Una vuelta: corre, una tras otra, cada {@link TareaProgramada} a la que le toca (ADR-0104).
 *
 * <p>La dispara Cloud Scheduler en los ambientes desplegados, y un {@code @Scheduled} de un minuto
 * en local. Las tareas corren dentro de la llamada porque fuera de ella no hay CPU.
 *
 * <p><b>Le toca cuando pasó su intervalo desde que se tomó, menos un margen.</b> La vuelta no llega
 * siempre al mismo segundo: sin margen, una tarea de un minuto con vueltas de un minuto encontraría
 * casi siempre que faltan unos milisegundos, y correría una vuelta sí y otra no. El margen es de
 * {@value #MARGEN_MAXIMO_SEGUNDOS} segundos, o la mitad del intervalo si es más corto.
 *
 * <p><b>Una tarea que falla no detiene a las demás y queda anotada como terminada</b>: espera su
 * intervalo para volver a intentarlo, que es lo que hacía {@code @Scheduled} con una excepción.
 * Cada tarea ya está hecha para eso —lo que no se concilió en una vuelta se concilia en la
 * siguiente—, y reintentarla en cada vuelta a una tarea que falla por un tercero caído solo
 * multiplicaría las llamadas a ese tercero. Lo mismo si falla la bitácora al tomarla: esa tarea no
 * corre y las demás sí.
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

  static final long MARGEN_MAXIMO_SEGUNDOS = 30;

  private final List<TareaProgramada> tareas;
  private final BitacoraDeTareas bitacora;
  private final Reloj reloj;
  private final Duration arriendo;

  /**
   * @param arriendo cuánto tiempo es de quien la tomó una tarea que no termina. Mayor que lo que
   *     dura una petición, para que una vecina no la tome mientras todavía corre. No es una
   *     garantía: una petición que pasa su tiempo máximo deja el hilo congelado, no muerto, y puede
   *     seguir más tarde. Por eso soltar el arriendo exige ser su dueño ({@link
   *     BitacoraDeTareas#terminar}).
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
        // otra en cada vuelta, y la segunda no corre nunca sin que nada lo avise.
        throw new IllegalArgumentException("Dos tareas se llaman igual: " + tarea.nombre());
      }
    }
  }

  public ResultadoDeLaVuelta ejecutar() {
    List<String> corridas = new ArrayList<>();
    List<ResultadoDeLaVuelta.Fallo> fallidas = new ArrayList<>();
    int omitidas = 0;
    for (TareaProgramada tarea : tareas) {
      UUID quien = UUID.randomUUID();
      boolean tomada;
      try {
        tomada =
            bitacora.tomar(
                tarea.nombre(), aEsperar(tarea.intervalo()), arriendo, quien, reloj.ahora());
      } catch (RuntimeException e) {
        fallidas.add(new ResultadoDeLaVuelta.Fallo(tarea.nombre(), motivo(e)));
        continue;
      }
      if (!tomada) {
        omitidas++;
        continue;
      }
      String fallo = null;
      try {
        tarea.ejecutar();
      } catch (RuntimeException e) {
        fallo = motivo(e);
      }
      try {
        bitacora.terminar(tarea.nombre(), quien, reloj.ahora(), fallo);
      } catch (RuntimeException e) {
        // La tarea hizo su trabajo; lo que no quedó es la anotación. Su arriendo vence y vuelve a
        // tomarse, igual que si la instancia hubiera muerto.
        fallo = fallo != null ? fallo : motivo(e);
      }
      if (fallo == null) {
        corridas.add(tarea.nombre());
      } else {
        fallidas.add(new ResultadoDeLaVuelta.Fallo(tarea.nombre(), fallo));
      }
    }
    return new ResultadoDeLaVuelta(corridas, fallidas, omitidas);
  }

  static Duration aEsperar(Duration intervalo) {
    Duration margen = intervalo.dividedBy(2);
    Duration maximo = Duration.ofSeconds(MARGEN_MAXIMO_SEGUNDOS);
    return intervalo.minus(margen.compareTo(maximo) < 0 ? margen : maximo);
  }

  private static String motivo(RuntimeException e) {
    String motivo = e.toString();
    return motivo.length() <= LARGO_MAXIMO_DEL_MOTIVO
        ? motivo
        : motivo.substring(0, LARGO_MAXIMO_DEL_MOTIVO);
  }
}
