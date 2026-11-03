package co.tecnosport.api.application.compartido;

import java.util.List;

/**
 * Qué hizo una vuelta: las tareas que corrieron bien, las que fallaron con su motivo, y cuántas no
 * corrieron porque no les tocaba, porque otra instancia las tenía o porque era la primera vez que
 * se veían.
 *
 * <p>Que una falle no hace fallar la vuelta: las otras ya hicieron su trabajo, y repetirlas porque
 * una vecina se cayó no arregla nada.
 */
public record ResultadoDeLaVuelta(List<String> corridas, List<Fallo> fallidas, int omitidas) {

  public ResultadoDeLaVuelta {
    corridas = List.copyOf(corridas);
    fallidas = List.copyOf(fallidas);
  }

  public record Fallo(String tarea, String motivo) {}
}
