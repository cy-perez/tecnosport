package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SolicitarRecuperacionTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);
  private static final String URL_BASE = "http://localhost:4200/es/cuenta/restablecer-clave";
  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(15);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioTokensRecuperacionFalso tokens;
  private EnviadorDeCorreoFalso enviador;
  private LimitadorDeIntentosFalso limitadorDeIntentos;

  private SolicitarRecuperacion crear() {
    usuarios = new RepositorioUsuariosFalso();
    tokens = new RepositorioTokensRecuperacionFalso();
    enviador = new EnviadorDeCorreoFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    return new SolicitarRecuperacion(
        usuarios,
        tokens,
        enviador,
        new RelojFalso(AHORA),
        VIGENCIA_TOKEN,
        URL_BASE,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA);
  }

  private void conUsuario(String correo) {
    Usuario usuario = Usuario.crear(new CorreoElectronico(correo), "hash", Rol.CLIENTE, AHORA);
    usuario.verificarCorreo(AHORA);
    usuarios.conUsuario(usuario);
  }

  @Test
  void conUnCorreoExistenteCreaUnTokenYEnviaElCorreo() {
    SolicitarRecuperacion caso = crear();
    conUsuario("cliente@tecnosport.co");

    caso.ejecutar(new SolicitarRecuperacionComando("cliente@tecnosport.co"));

    assertEquals(1, tokens.todos().size());
    assertEquals(1, enviador.enviados().size());
    EnviadorDeCorreoFalso.CorreoEnviado correo = enviador.enviados().get(0);
    assertEquals(new CorreoElectronico("cliente@tecnosport.co"), correo.destinatario());
    assertTrue(correo.cuerpoHtml().contains(URL_BASE));
    assertTrue(correo.cuerpoHtml().contains(tokens.todos().get(0).id().toString()));
  }

  @Test
  void conUnCorreoInexistenteNoHaceNada() {
    SolicitarRecuperacion caso = crear();

    caso.ejecutar(new SolicitarRecuperacionComando("no-existe@tecnosport.co"));

    assertEquals(0, tokens.todos().size());
    assertEquals(0, enviador.enviados().size());
  }

  @Test
  void excederElLimiteDeIntentosPorCuentaLanzaLimiteDeIntentosExcedidoAunSinCorreoExistente() {
    SolicitarRecuperacion caso = crear();
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso.ejecutar(new SolicitarRecuperacionComando("no-existe@tecnosport.co")));
  }
}
