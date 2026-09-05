package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClaveInvalidoException;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConfirmarRecuperacion {

  private final RepositorioTokensRecuperacion repositorioTokens;
  private final RepositorioUsuarios repositorioUsuarios;
  private final RepositorioSesiones repositorioSesiones;
  private final CodificadorDeClaves codificadorDeClaves;
  private final Reloj reloj;

  public ConfirmarRecuperacion(
      RepositorioTokensRecuperacion repositorioTokens,
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      Reloj reloj) {
    this.repositorioTokens = Objects.requireNonNull(repositorioTokens);
    this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
    this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
    this.codificadorDeClaves = Objects.requireNonNull(codificadorDeClaves);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void ejecutar(ConfirmarRecuperacionComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    UUID tokenId;
    try {
      tokenId = UUID.fromString(comando.token());
    } catch (IllegalArgumentException | NullPointerException excepcion) {
      throw new TokenRecuperacionClaveInvalidoException();
    }

    TokenRecuperacionClave token =
        repositorioTokens
            .buscarPorId(tokenId)
            .orElseThrow(TokenRecuperacionClaveInvalidoException::new);

    Instant ahora = reloj.ahora();
    token.marcarUsado(ahora);
    repositorioTokens.guardar(token);

    Usuario usuario =
        repositorioUsuarios
            .buscarPorId(token.usuarioId())
            .orElseThrow(TokenRecuperacionClaveInvalidoException::new);
    usuario.cambiarClave(codificadorDeClaves.codificar(comando.claveNuevaTextoPlano()));
    repositorioUsuarios.guardar(usuario);

    repositorioSesiones.revocarTodasDeUsuario(usuario.id(), ahora);
  }
}
