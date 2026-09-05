package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.application.usuario.RepositorioTokensRecuperacion;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.infrastructure.usuario.entidad.TokenRecuperacionClaveJpaEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RepositorioTokensRecuperacionJpa implements RepositorioTokensRecuperacion {

  private final TokenRecuperacionClaveJpaRepository tokens;

  public RepositorioTokensRecuperacionJpa(TokenRecuperacionClaveJpaRepository tokens) {
    this.tokens = Objects.requireNonNull(tokens);
  }

  @Override
  public Optional<TokenRecuperacionClave> buscarPorId(UUID id) {
    return tokens.findById(id).map(this::aDominio);
  }

  @Override
  public void guardar(TokenRecuperacionClave token) {
    tokens.save(aEntidad(token));
  }

  private TokenRecuperacionClave aDominio(TokenRecuperacionClaveJpaEntity entidad) {
    return new TokenRecuperacionClave(
        entidad.getId(),
        entidad.getUsuarioId(),
        entidad.getCreadoEn(),
        entidad.getExpiraEn(),
        entidad.getUsadoEn());
  }

  private TokenRecuperacionClaveJpaEntity aEntidad(TokenRecuperacionClave token) {
    return new TokenRecuperacionClaveJpaEntity(
        token.id(),
        token.usuarioId(),
        token.creadoEn(),
        token.expiraEn(),
        token.usadoEn().orElse(null));
  }
}
