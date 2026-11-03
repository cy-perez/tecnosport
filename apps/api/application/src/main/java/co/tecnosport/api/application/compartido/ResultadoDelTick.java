package co.tecnosport.api.application.compartido;

import java.util.List;

/**
 * Qué hizo un tick: las tareas que corrieron bien, las que fallaron con su motivo, y cuántas no
 * corrieron porque no les tocaba o porque otra instancia las tenía.
 *
 * <p>Que una falle no hace fallar el tick: las otras ya hicieron su trabajo, y repetirlas porque
 * una vecina se cayó no arregla nada.
 */
public record ResultadoDelTick(List<String> corridas, List<Fallo> fallidas, int omitidas) {

  public ResultadoDelTick {
    corridas = List.copyOf(corridas);
    fallidas = List.copyOf(fallidas);
  }

  public record Fallo(String tarea, String motivo) {}
}
