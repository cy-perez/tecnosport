package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.RepositorioTokensVerificacion;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioTokensVerificacionDobleDePrueba implements RepositorioTokensVerificacion {

  private final Map<UUID, TokenVerificacionCorreo> tokens = new HashMap<>();

  void conToken(TokenVerificacionCorreo token) {
    tokens.put(token.id(), token);
  }

  @Override
  public Optional<TokenVerificacionCorreo> buscarPorId(UUID id) {
    return Optional.ofNullable(tokens.get(id));
  }

  @Override
  public void guardar(TokenVerificacionCorreo token) {
    tokens.put(token.id(), token);
  }
}
