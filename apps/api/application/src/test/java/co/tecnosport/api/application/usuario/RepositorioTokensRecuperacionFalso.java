package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioTokensRecuperacionFalso implements RepositorioTokensRecuperacion {

  private final List<TokenRecuperacionClave> tokens = new ArrayList<>();

  @Override
  public Optional<TokenRecuperacionClave> buscarPorId(UUID id) {
    return tokens.stream().filter(t -> t.id().equals(id)).findFirst();
  }

  @Override
  public void guardar(TokenRecuperacionClave token) {
    tokens.removeIf(t -> t.id().equals(token.id()));
    tokens.add(token);
  }

  List<TokenRecuperacionClave> todos() {
    return List.copyOf(tokens);
  }
}
