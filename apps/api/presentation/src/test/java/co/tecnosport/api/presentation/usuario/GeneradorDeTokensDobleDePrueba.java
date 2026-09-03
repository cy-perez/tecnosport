package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;

final class GeneradorDeTokensDobleDePrueba implements GeneradorDeTokens {

  @Override
  public String generarAcceso(Usuario usuario, Instant ahora) {
    return "token-de-prueba:" + usuario.id();
  }
}
