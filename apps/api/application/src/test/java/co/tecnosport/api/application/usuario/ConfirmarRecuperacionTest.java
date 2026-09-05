package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClaveInvalidoException;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmarRecuperacionTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioTokensRecuperacionFalso tokens;
  private RepositorioSesionesFalso sesiones;
  private CodificadorDeClavesFalso codificador;

  private ConfirmarRecuperacion crear() {
    usuarios = new RepositorioUsuariosFalso();
    tokens = new RepositorioTokensRecuperacionFalso();
    sesiones = new RepositorioSesionesFalso();
    codificador = new CodificadorDeClavesFalso();
    return new ConfirmarRecuperacion(
        tokens, usuarios, sesiones, codificador, new RelojFalso(AHORA));
  }

  private Usuario conUsuario() {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("cliente@tecnosport.co"), "hash-viejo", Rol.CLIENTE, AHORA);
    usuarios.conUsuario(usuario);
    return usuario;
  }

  @Test
  void confirmarConTokenVigenteCambiaLaClave() {
    ConfirmarRecuperacion caso = crear();
    Usuario usuario = conUsuario();
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(usuario.id(), AHORA, VIGENCIA_TOKEN);
    tokens.guardar(token);

    caso.ejecutar(new ConfirmarRecuperacionComando(token.id().toString(), "clave-nueva"));

    Usuario actualizado = usuarios.buscarPorId(usuario.id()).orElseThrow();
    assertTrue(codificador.verificar("clave-nueva", actualizado.claveHash()));
    assertTrue(tokens.buscarPorId(token.id()).orElseThrow().usadoEn().isPresent());
  }

  @Test
  void confirmarRevocaTodasLasSesionesDelUsuario() {
    ConfirmarRecuperacion caso = crear();
    Usuario usuario = conUsuario();
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(usuario.id(), AHORA, VIGENCIA_TOKEN);
    tokens.guardar(token);
    SesionRefresco sesionA =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), AHORA, VIGENCIA_REFRESCO);
    SesionRefresco sesionB =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), AHORA, VIGENCIA_REFRESCO);
    sesiones.guardar(sesionA);
    sesiones.guardar(sesionB);

    caso.ejecutar(new ConfirmarRecuperacionComando(token.id().toString(), "clave-nueva"));

    assertTrue(sesiones.todas().stream().allMatch(s -> s.revocadoEn().isPresent()));
  }

  @Test
  void confirmarConTokenYaUsadoLanzaInvalido() {
    ConfirmarRecuperacion caso = crear();
    Usuario usuario = conUsuario();
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(usuario.id(), AHORA, VIGENCIA_TOKEN);
    token.marcarUsado(AHORA);
    tokens.guardar(token);

    assertThrows(
        TokenRecuperacionClaveInvalidoException.class,
        () ->
            caso.ejecutar(new ConfirmarRecuperacionComando(token.id().toString(), "clave-nueva")));
  }

  @Test
  void confirmarConTokenVencidoLanzaInvalido() {
    ConfirmarRecuperacion caso = crear();
    Usuario usuario = conUsuario();
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(
            usuario.id(), AHORA.minus(VIGENCIA_TOKEN.plusSeconds(1)), VIGENCIA_TOKEN);
    tokens.guardar(token);

    assertThrows(
        TokenRecuperacionClaveInvalidoException.class,
        () ->
            caso.ejecutar(new ConfirmarRecuperacionComando(token.id().toString(), "clave-nueva")));
  }

  @Test
  void confirmarConTokenInexistenteLanzaInvalido() {
    ConfirmarRecuperacion caso = crear();

    assertThrows(
        TokenRecuperacionClaveInvalidoException.class,
        () ->
            caso.ejecutar(
                new ConfirmarRecuperacionComando(UUID.randomUUID().toString(), "clave-nueva")));
  }

  @Test
  void confirmarConTokenMalFormadoLanzaInvalido() {
    ConfirmarRecuperacion caso = crear();

    assertThrows(
        TokenRecuperacionClaveInvalidoException.class,
        () -> caso.ejecutar(new ConfirmarRecuperacionComando("no-es-un-uuid", "clave-nueva")));
  }
}
