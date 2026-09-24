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

  /**
   * Con el filtro de "todavía no revocada", como la consulta real.
   *
   * <p>Sin él, este doble le reescribía el {@code revocadoEn} a una sesión ya revocada, que es un
   * dato de auditoría: cuándo se cerró, no cuándo se volvió a tocar. Las dos consultas de verdad
   * llevan {@code where s.revocadoEn is null}.
   *
   * <p><b>Lo que este doble no puede reproducir</b> —y no se intenta— es el {@code
   * flushAutomatically}/{@code clearAutomatically} de Hibernate, que es de donde salió el defecto
   * que costó una sesión de depuración: {@code ConfirmarRecuperacion} perdía en silencio el token
   * consumido y la clave nueva. Eso está cubierto donde se puede, contra Postgres real, en {@code
   * ConfirmarRecuperacionIntegracionTest}.
   */
  @Override
  public void revocarFamilia(UUID familiaId, Instant ahora) {
    sesiones.stream()
        .filter(s -> s.familiaId().equals(familiaId) && s.revocadoEn().isEmpty())
        .forEach(s -> s.revocar(ahora));
  }

  @Override
  public void revocarTodasDeUsuario(UUID usuarioId, Instant ahora) {
    sesiones.stream()
        .filter(s -> s.usuarioId().equals(usuarioId) && s.revocadoEn().isEmpty())
        .forEach(s -> s.revocar(ahora));
  }

  void limpiar() {
    sesiones.clear();
  }
}
