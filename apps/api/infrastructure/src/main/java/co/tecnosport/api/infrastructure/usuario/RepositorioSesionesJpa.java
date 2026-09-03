package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.infrastructure.usuario.entidad.SesionRefrescoJpaEntity;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RepositorioSesionesJpa implements RepositorioSesiones {

  private final SesionRefrescoJpaRepository sesiones;

  public RepositorioSesionesJpa(SesionRefrescoJpaRepository sesiones) {
    this.sesiones = Objects.requireNonNull(sesiones);
  }

  @Override
  public void guardar(SesionRefresco sesion) {
    sesiones.save(aEntidad(sesion));
  }

  @Override
  public Optional<SesionRefresco> buscarPorId(UUID id) {
    return sesiones.findById(id).map(this::aDominio);
  }

  @Override
  public void revocarFamilia(UUID familiaId, Instant ahora) {
    sesiones.revocarFamilia(familiaId, ahora);
  }

  private SesionRefresco aDominio(SesionRefrescoJpaEntity entidad) {
    return new SesionRefresco(
        entidad.getId(),
        entidad.getUsuarioId(),
        entidad.getFamiliaId(),
        entidad.getCreadoEn(),
        entidad.getExpiraEn(),
        entidad.getUsadoEn(),
        entidad.getRevocadoEn());
  }

  private SesionRefrescoJpaEntity aEntidad(SesionRefresco sesion) {
    return new SesionRefrescoJpaEntity(
        sesion.id(),
        sesion.usuarioId(),
        sesion.familiaId(),
        sesion.creadoEn(),
        sesion.expiraEn(),
        sesion.usadoEn().orElse(null),
        sesion.revocadoEn().orElse(null));
  }
}
