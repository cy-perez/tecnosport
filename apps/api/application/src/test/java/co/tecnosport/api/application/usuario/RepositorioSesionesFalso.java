package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.SesionRefresco;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioSesionesFalso implements RepositorioSesiones {

  private final List<SesionRefresco> sesiones = new ArrayList<>();

  @Override
  public void guardar(SesionRefresco sesion) {
    sesiones.removeIf(s -> s.id().equals(sesion.id()));
    sesiones.add(sesion);
  }

  @Override
  public Optional<SesionRefresco> buscarPorId(UUID id) {
    return sesiones.stream().filter(s -> s.id().equals(id)).findFirst();
  }

  @Override
  public void revocarFamilia(UUID familiaId, Instant ahora) {
    sesiones.stream().filter(s -> s.familiaId().equals(familiaId)).forEach(s -> s.revocar(ahora));
  }

  List<SesionRefresco> todas() {
    return List.copyOf(sesiones);
  }
}
