package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreoInvalidoException;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class VerificarCorreo {

  private final RepositorioTokensVerificacion repositorioTokens;
  private final RepositorioUsuarios repositorioUsuarios;
  private final Reloj reloj;

  public VerificarCorreo(
      RepositorioTokensVerificacion repositorioTokens,
      RepositorioUsuarios repositorioUsuarios,
      Reloj reloj) {
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void ejecutar(VerificarCorreoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    UUID tokenId;
    try {
      tokenId = UUID.fromString(comando.token());
    } catch (IllegalArgumentException | NullPointerException excepcion) {
      throw new TokenVerificacionCorreoInvalidoException();
    }

    TokenVerificacionCorreo token =
        repositorioTokens
            .buscarPorId(tokenId)
            .orElseThrow(TokenVerificacionCorreoInvalidoException::new);

    Instant ahora = reloj.ahora();
    token.marcarUsado(ahora);
    repositorioTokens.guardar(token);

    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(token.usuarioId())
            .orElseThrow(TokenVerificacionCorreoInvalidoException::new);
    usuario.verificarCorreo(ahora);
    repositorioUsuarios.guardar(usuario);
  }
}
