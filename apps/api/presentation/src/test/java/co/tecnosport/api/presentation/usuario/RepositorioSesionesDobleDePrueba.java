package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RepositorioSesionesDobleDePrueba implements RepositorioSesiones {

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

  @Override
  public void revocarTodasDeUsuario(UUID usuarioId, Instant ahora) {
    sesiones.stream().filter(s -> s.usuarioId().equals(usuarioId)).forEach(s -> s.revocar(ahora));
  }
}
