package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La semántica del puerto en memoria: la primera vez solo anota, le toca por intervalo desde que se
 * tomó, el arriendo vigente la reserva, y solo su dueño lo suelta.
 */
public final class BitacoraDeTareasFalsa implements BitacoraDeTareas {

  public record Fila(
      Instant tomadaEn,
      Instant terminadaEn,
      Instant enCursoHasta,
      UUID arrendadaPor,
      String fallo) {}

  private final Map<String, Fila> filas = new HashMap<>();
  public RuntimeException fallarAlTomar;

  @Override
  public boolean tomar(
      String nombre, Duration intervalo, Duration arriendo, UUID quien, Instant ahora) {
    if (fallarAlTomar != null) {
      throw fallarAlTomar;
    }
    Fila fila = filas.get(nombre);
    if (fila == null) {
      filas.put(nombre, new Fila(ahora, null, null, null, null));
      return false;
    }
    boolean leToca = !fila.tomadaEn().plus(intervalo).isAfter(ahora);
    boolean libre = fila.enCursoHasta() == null || !fila.enCursoHasta().isAfter(ahora);
    if (!leToca || !libre) {
      return false;
    }
    filas.put(
        nombre, new Fila(ahora, fila.terminadaEn(), ahora.plus(arriendo), quien, fila.fallo()));
    return true;
  }

  @Override
  public void terminar(String nombre, UUID quien, Instant terminadaEn, String fallo) {
    Fila fila = filas.get(nombre);
    if (fila == null || !quien.equals(fila.arrendadaPor())) {
      return;
    }
    filas.put(nombre, new Fila(fila.tomadaEn(), terminadaEn, null, null, fallo));
  }

  /**
   * Como si la tarea ya existiera en la bitácora y se hubiera tomado por última vez en {@code en}.
   */
  public void vista(String nombre, Instant en) {
    filas.put(nombre, new Fila(en, en, null, null, null));
  }

  /** Para montar el escenario de una vecina que tomó la tarea y sigue con ella. */
  public void tomadaPorOtra(String nombre, Instant desde, Instant hasta) {
    filas.put(nombre, new Fila(desde, null, hasta, UUID.randomUUID(), null));
  }

  public Fila fila(String nombre) {
    return filas.get(nombre);
  }
}
