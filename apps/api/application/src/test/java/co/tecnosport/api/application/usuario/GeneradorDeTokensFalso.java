package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class GeneradorDeTokensFalso implements GeneradorDeTokens {

  @Override
  public String generarAcceso(Usuario usuario, Instant ahora) {
    return "token-de-prueba:" + usuario.id();
  }
}
