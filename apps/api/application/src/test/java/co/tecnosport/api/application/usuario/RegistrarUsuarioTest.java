package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.CorreoYaRegistradoException;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegistrarUsuarioTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_TOKEN = Duration.ofHours(24);
  private static final String URL_BASE = "http://localhost:4200/es/cuenta/verificar-correo";
  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(15);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioTokensVerificacionFalso tokens;
  private EnviadorDeCorreoFalso enviador;
  private LimitadorDeIntentosFalso limitadorDeIntentos;

  private RegistrarUsuario crear() {
    usuarios = new RepositorioUsuariosFalso();
    tokens = new RepositorioTokensVerificacionFalso();
    enviador = new EnviadorDeCorreoFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    return new RegistrarUsuario(
        usuarios,
        tokens,
        new CodificadorDeClavesFalso(),
        enviador,
        new RelojFalso(AHORA),
        VIGENCIA_TOKEN,
        URL_BASE,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA);
  }

  @Test
  void registrarCreaUnClienteSinVerificar() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(new RegistrarUsuarioComando("cliente@tecnosport.co", "clave-segura"));

    Usuario usuario =
        usuarios.buscarPorCorreo(new CorreoElectronico("cliente@tecnosport.co")).orElseThrow();
    assertEquals(Rol.CLIENTE, usuario.rol());
    assertFalse(usuario.correoVerificado());
  }

  @Test
  void registrarCreaUnTokenDeVerificacionYEnviaElCorreo() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(new RegistrarUsuarioComando("cliente@tecnosport.co", "clave-segura"));

    assertEquals(1, tokens.todos().size());
    assertEquals(1, enviador.enviados().size());
    EnviadorDeCorreoFalso.CorreoEnviado correo = enviador.enviados().get(0);
    assertEquals(new CorreoElectronico("cliente@tecnosport.co"), correo.destinatario());
    assertTrue(correo.cuerpoHtml().contains(URL_BASE));
    assertTrue(correo.cuerpoHtml().contains(tokens.todos().get(0).id().toString()));
  }

  @Test
  void registrarConCorreoYaExistenteLanzaCorreoYaRegistrado() {
    RegistrarUsuario caso = crear();
    caso.ejecutar(new RegistrarUsuarioComando("cliente@tecnosport.co", "clave-segura"));

    assertThrows(
        CorreoYaRegistradoException.class,
        () -> caso.ejecutar(new RegistrarUsuarioComando("cliente@tecnosport.co", "otra-clave")));
  }

  @Test
  void excederElLimiteDeIntentosPorCuentaLanzaLimiteDeIntentosExcedido() {
    RegistrarUsuario caso = crear();
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso.ejecutar(new RegistrarUsuarioComando("cliente@tecnosport.co", "clave-segura")));
  }
}
