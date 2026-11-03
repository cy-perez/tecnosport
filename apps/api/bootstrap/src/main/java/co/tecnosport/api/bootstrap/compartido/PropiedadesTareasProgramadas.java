package co.tecnosport.api.bootstrap.compartido;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quién dispara el tick de las tareas programadas (ADR-0104).
 *
 * <p>Con {@code interno} —local y pruebas— un {@code @Scheduled} de un minuto llama al tick dentro
 * de la aplicación. Con {@code externo} lo llama Cloud Scheduler, y entonces la cuenta, la
 * audiencia y las llaves son obligatorias: el arranque falla si falta alguna, antes de que todos
 * los ticks reboten con un 401 que nadie mira.
 *
 * @param disparador {@code interno} o {@code externo}
 * @param cuentaDeServicio la cuenta con que Cloud Scheduler firma el token de cada tick
 * @param audiencia la audiencia de ese token; la URL del servicio
 * @param urlLlaves las llaves públicas con que Google firma los tokens
 * @param arriendo cuánto es de quien la tomó una tarea que no termina; más que el tiempo máximo de
 *     una petición, que es lo más que puede durar
 */
@ConfigurationProperties(prefix = "tecnosport.tareas")
public record PropiedadesTareasProgramadas(
    Disparador disparador,
    String cuentaDeServicio,
    String audiencia,
    String urlLlaves,
    Duration arriendo) {

  public enum Disparador {
    INTERNO,
    EXTERNO
  }

  public PropiedadesTareasProgramadas {
    if (disparador == null) {
      throw new IllegalStateException(
          "Falta tecnosport.tareas.disparador: interno o externo (ADR-0104).");
    }
    if (arriendo == null || arriendo.isNegative() || arriendo.isZero()) {
      throw new IllegalStateException("tecnosport.tareas.arriendo tiene que ser mayor que cero.");
    }
    if (disparador == Disparador.EXTERNO) {
      exigir("cuenta-de-servicio", cuentaDeServicio);
      exigir("audiencia", audiencia);
      exigir("url-llaves", urlLlaves);
    }
  }

  private static void exigir(String nombre, String valor) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(
          "Con el disparador externo, falta tecnosport.tareas." + nombre + ".");
    }
  }

  public boolean externo() {
    return disparador == Disparador.EXTERNO;
  }
}
