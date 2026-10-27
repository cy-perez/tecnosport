package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import co.tecnosport.api.application.compartido.RespuestaIdempotente;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Sin lógica de vencimiento:
 * eso ya lo cubre {@code RepositorioIdempotenciaJpaTest} contra Postgres real.
 */
final class RepositorioIdempotenciaFalso implements RepositorioIdempotencia {

  private final Set<String> reclamadas = new HashSet<>();
  private final Map<String, String[]> operaciones = new HashMap<>();
  private final Map<String, RespuestaIdempotente> completadas = new HashMap<>();

  @Override
  public Optional<RespuestaIdempotente> buscarCompletada(String llave, Instant ahora) {
    return Optional.ofNullable(completadas.get(llave));
  }

  @Override
  public boolean reclamar(String llave, String metodo, String ruta, Instant ahora) {
    boolean reclamada = reclamadas.add(llave);
    if (reclamada) {
      operaciones.put(llave, new String[] {metodo, ruta});
    }
    return reclamada;
  }

  /** Como el adaptador real: el método y la ruta salen de lo que se reclamó. */
  @Override
  public void completar(String llave, RespuestaIdempotente respuesta, Instant ahora) {
    String[] operacion = operaciones.get(llave);
    completadas.put(
        llave,
        new RespuestaIdempotente(
            respuesta.estadoHttp(),
            respuesta.tipoContenido(),
            respuesta.cuerpo(),
            operacion[0],
            operacion[1]));
  }

  @Override
  public int purgarVencidas(Instant ahora) {
    return 0;
  }

  @Override
  public void liberar(String llave) {
    reclamadas.remove(llave);
    operaciones.remove(llave);
    completadas.remove(llave);
  }
}
