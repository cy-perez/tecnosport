package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreoInvalidoException;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificarCorreoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_TOKEN = Duration.ofHours(24);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioTokensVerificacionFalso tokens;

  private VerificarCorreo crear() {
    usuarios = new RepositorioUsuariosFalso();
    tokens = new RepositorioTokensVerificacionFalso();
    return new VerificarCorreo(tokens, usuarios, new RelojFalso(AHORA));
  }

  private Usuario conUsuarioSinVerificar() {
    Usuario usuario =
        Usuario.crear(new CorreoElectronico("cliente@tecnosport.co"), "hash", Rol.CLIENTE, AHORA);
    usuarios.conUsuario(usuario);
    return usuario;
  }

  @Test
  void verificarConTokenVigenteMarcaElUsuarioVerificado() {
    VerificarCorreo caso = crear();
    Usuario usuario = conUsuarioSinVerificar();
    TokenVerificacionCorreo token =
        TokenVerificacionCorreo.crear(usuario.id(), AHORA, VIGENCIA_TOKEN);
    tokens.guardar(token);

    caso.ejecutar(new VerificarCorreoComando(token.id().toString()));

    Usuario verificado = usuarios.buscarPorId(usuario.id()).orElseThrow();
    assertTrue(verificado.correoVerificado());
    assertTrue(tokens.buscarPorId(token.id()).orElseThrow().usadoEn().isPresent());
  }

  @Test
  void verificarConTokenYaUsadoLanzaInvalido() {
    VerificarCorreo caso = crear();
    Usuario usuario = conUsuarioSinVerificar();
    TokenVerificacionCorreo token =
        TokenVerificacionCorreo.crear(usuario.id(), AHORA, VIGENCIA_TOKEN);
    token.marcarUsado(AHORA);
    tokens.guardar(token);

    assertThrows(
        TokenVerificacionCorreoInvalidoException.class,
        () -> caso.ejecutar(new VerificarCorreoComando(token.id().toString())));
  }

  @Test
  void verificarConTokenVencidoLanzaInvalido() {
    VerificarCorreo caso = crear();
    Usuario usuario = conUsuarioSinVerificar();
    TokenVerificacionCorreo token =
        TokenVerificacionCorreo.crear(
            usuario.id(), AHORA.minus(VIGENCIA_TOKEN.plusSeconds(1)), VIGENCIA_TOKEN);
    tokens.guardar(token);

    assertThrows(
        TokenVerificacionCorreoInvalidoException.class,
        () -> caso.ejecutar(new VerificarCorreoComando(token.id().toString())));
  }

  @Test
  void verificarConTokenInexistenteLanzaInvalido() {
    VerificarCorreo caso = crear();

    assertThrows(
        TokenVerificacionCorreoInvalidoException.class,
        () -> caso.ejecutar(new VerificarCorreoComando(UUID.randomUUID().toString())));
  }

  @Test
  void verificarConTokenMalFormadoLanzaInvalido() {
    VerificarCorreo caso = crear();

    assertThrows(
        TokenVerificacionCorreoInvalidoException.class,
        () -> caso.ejecutar(new VerificarCorreoComando("no-es-un-uuid")));
  }
}
