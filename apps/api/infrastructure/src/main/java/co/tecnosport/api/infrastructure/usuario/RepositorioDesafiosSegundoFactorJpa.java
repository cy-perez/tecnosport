package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.RepositorioDesafiosSegundoFactor;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.HuellaDeToken;
import co.tecnosport.api.infrastructure.usuario.entidad.DesafioSegundoFactorJpaEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RepositorioDesafiosSegundoFactorJpa implements RepositorioDesafiosSegundoFactor {

  private final DesafioSegundoFactorJpaRepository desafios;

  public RepositorioDesafiosSegundoFactorJpa(DesafioSegundoFactorJpaRepository desafios) {
    this.desafios = Objects.requireNonNull(desafios);
  }

  @Override
  public Optional<DesafioSegundoFactor> buscarParaConsumir(UUID id) {
    // Por la huella, y el id se le devuelve al agregado desde quien lo trajo: la base no lo tiene.
    return desafios.findParaConsumir(HuellaDeToken.de(id)).map(e -> aDominio(id, e));
  }

  @Override
  public void guardar(DesafioSegundoFactor desafio) {
    desafios.save(
        new DesafioSegundoFactorJpaEntity(
            HuellaDeToken.de(desafio.id()),
            desafio.usuarioId(),
            desafio.creadoEn(),
            desafio.expiraEn(),
            desafio.usadoEn().orElse(null)));
  }

  private static DesafioSegundoFactor aDominio(UUID id, DesafioSegundoFactorJpaEntity entidad) {
    return new DesafioSegundoFactor(
        id,
        entidad.getUsuarioId(),
        entidad.getCreadoEn(),
        entidad.getExpiraEn(),
        entidad.getUsadoEn());
  }
}
