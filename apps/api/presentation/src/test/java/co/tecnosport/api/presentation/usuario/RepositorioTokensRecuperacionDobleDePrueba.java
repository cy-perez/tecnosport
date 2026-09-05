package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.RepositorioTokensRecuperacion;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioTokensRecuperacionDobleDePrueba implements RepositorioTokensRecuperacion {

  private final Map<UUID, TokenRecuperacionClave> tokens = new HashMap<>();

  void conToken(TokenRecuperacionClave token) {
    tokens.put(token.id(), token);
  }

  @Override
  public Optional<TokenRecuperacionClave> buscarPorId(UUID id) {
    return Optional.ofNullable(tokens.get(id));
  }

  @Override
  public void guardar(TokenRecuperacionClave token) {
    tokens.put(token.id(), token);
  }
}
