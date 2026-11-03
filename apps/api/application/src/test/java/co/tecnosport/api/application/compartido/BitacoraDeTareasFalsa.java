package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** La semántica del puerto en memoria: le toca por intervalo, y el arriendo vigente la reserva. */
public final class BitacoraDeTareasFalsa implements BitacoraDeTareas {

  public record Fila(Instant terminadaEn, Instant enCursoHasta, String fallo) {}

  private final Map<String, Fila> filas = new HashMap<>();

  @Override
  public boolean tomar(String nombre, Duration intervalo, Duration arriendo, Instant ahora) {
    Fila fila = filas.get(nombre);
    if (fila != null) {
      boolean leToca =
          fila.terminadaEn() == null || !fila.terminadaEn().plus(intervalo).isAfter(ahora);
      boolean libre = fila.enCursoHasta() == null || !fila.enCursoHasta().isAfter(ahora);
      if (!leToca || !libre) {
        return false;
      }
    }
    Instant terminadaEn = fila == null ? null : fila.terminadaEn();
    String fallo = fila == null ? null : fila.fallo();
    filas.put(nombre, new Fila(terminadaEn, ahora.plus(arriendo), fallo));
    return true;
  }

  @Override
  public void terminar(String nombre, Instant terminadaEn, String fallo) {
    filas.put(nombre, new Fila(terminadaEn, null, fallo));
  }

  /** Para montar el escenario de una vecina que tomó la tarea y sigue con ella. */
  public void tomadaPorOtra(String nombre, Instant hasta) {
    filas.put(nombre, new Fila(null, hasta, null));
  }

  public Fila fila(String nombre) {
    return filas.get(nombre);
  }
}
