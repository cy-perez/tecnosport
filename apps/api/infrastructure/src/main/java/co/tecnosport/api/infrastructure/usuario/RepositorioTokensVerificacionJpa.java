package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.RepositorioTokensVerificacion;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import co.tecnosport.api.infrastructure.usuario.entidad.TokenVerificacionCorreoJpaEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RepositorioTokensVerificacionJpa implements RepositorioTokensVerificacion {

  private final TokenVerificacionCorreoJpaRepository tokens;

  public RepositorioTokensVerificacionJpa(TokenVerificacionCorreoJpaRepository tokens) {
    this.tokens = Objects.requireNonNull(tokens);
  }

  @Override
  public Optional<TokenVerificacionCorreo> buscarPorId(UUID id) {
    return tokens.findById(id).map(this::aDominio);
  }

  @Override
  public void guardar(TokenVerificacionCorreo token) {
    tokens.save(aEntidad(token));
  }

  private TokenVerificacionCorreo aDominio(TokenVerificacionCorreoJpaEntity entidad) {
    return new TokenVerificacionCorreo(
        entidad.getId(),
        entidad.getUsuarioId(),
        entidad.getCreadoEn(),
        entidad.getExpiraEn(),
        entidad.getUsadoEn());
  }

  private TokenVerificacionCorreoJpaEntity aEntidad(TokenVerificacionCorreo token) {
    return new TokenVerificacionCorreoJpaEntity(
        token.id(),
        token.usuarioId(),
        token.creadoEn(),
        token.expiraEn(),
        token.usadoEn().orElse(null));
  }
}
