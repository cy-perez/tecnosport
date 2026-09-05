package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioTokensVerificacionFalso implements RepositorioTokensVerificacion {

  private final List<TokenVerificacionCorreo> tokens = new ArrayList<>();

  @Override
  public Optional<TokenVerificacionCorreo> buscarPorId(UUID id) {
    return tokens.stream().filter(t -> t.id().equals(id)).findFirst();
  }

  @Override
  public void guardar(TokenVerificacionCorreo token) {
    tokens.removeIf(t -> t.id().equals(token.id()));
    tokens.add(token);
  }

  List<TokenVerificacionCorreo> todos() {
    return List.copyOf(tokens);
  }
}
