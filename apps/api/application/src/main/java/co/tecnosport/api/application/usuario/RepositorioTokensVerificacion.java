package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioTokensVerificacion {

  Optional<TokenVerificacionCorreo> buscarPorId(UUID id);

  void guardar(TokenVerificacionCorreo token);
}
