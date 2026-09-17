package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.RepositorioAcusesDeRevision;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioAcusesDobleDePrueba implements RepositorioAcusesDeRevision {

  private final List<AcuseDeRevision> acuses = new ArrayList<>();

  @Override
  public void guardar(AcuseDeRevision acuse) {
    acuses.add(acuse);
  }

  @Override
  public Map<UUID, Instant> ultimaRevisionDe(TipoDeRevision tipo, Collection<UUID> referencias) {
    Map<UUID, Instant> ultimas = new HashMap<>();
    for (AcuseDeRevision acuse : acuses) {
      if (acuse.tipo() != tipo || !referencias.contains(acuse.referencia())) {
        continue;
      }
      ultimas.merge(
          acuse.referencia(), acuse.revisadoEn(), (uno, otro) -> uno.isAfter(otro) ? uno : otro);
    }
    return ultimas;
  }
}
