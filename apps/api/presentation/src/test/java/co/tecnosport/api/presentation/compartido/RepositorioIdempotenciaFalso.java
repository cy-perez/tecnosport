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
  private final Map<String, RespuestaIdempotente> completadas = new HashMap<>();

  @Override
  public Optional<RespuestaIdempotente> buscarCompletada(String llave, Instant ahora) {
    return Optional.ofNullable(completadas.get(llave));
  }

  @Override
  public boolean reclamar(String llave, String metodo, String ruta, Instant ahora) {
    return reclamadas.add(llave);
  }

  @Override
  public void completar(String llave, RespuestaIdempotente respuesta, Instant ahora) {
    completadas.put(llave, respuesta);
  }

  @Override
  public void liberar(String llave) {
    reclamadas.remove(llave);
    completadas.remove(llave);
  }
}
