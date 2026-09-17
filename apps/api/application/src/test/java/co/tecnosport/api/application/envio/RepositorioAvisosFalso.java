package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.TipoDeRevision;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Reproduce la condición de la sentencia real —se gana el reclamo si no hay marca, o si la que
 * hay es anterior a la novedad— porque es justo lo que decide si sale un correo o no. Un doble que
 * siempre dijera {@code true} dejaría sin probar que no se avisa dos veces de lo mismo.
 */
final class RepositorioAvisosFalso implements RepositorioAvisosDeRevision {

  private final Map<String, Instant> avisados = new HashMap<>();

  @Override
  public boolean reclamarAviso(
      TipoDeRevision tipo, UUID referencia, Instant novedad, Instant ahora) {
    String llave = tipo.name() + ":" + referencia;
    Instant anterior = avisados.get(llave);
    if (anterior != null && !anterior.isBefore(novedad)) {
      return false;
    }
    avisados.put(llave, ahora);
    return true;
  }

  int avisados() {
    return avisados.size();
  }
}
