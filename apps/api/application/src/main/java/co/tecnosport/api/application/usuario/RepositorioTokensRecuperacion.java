package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioTokensRecuperacion {

  Optional<TokenRecuperacionClave> buscarPorId(UUID id);

  void guardar(TokenRecuperacionClave token);
}
