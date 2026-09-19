package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReenviarVerificacionTest {

  private static final Instant AHORA = Instant.parse("2026-09-19T12:00:00Z");
  private static final Duration VIGENCIA_TOKEN = Duration.ofHours(24);
  private static final String URL_BASE = "http://localhost:4200/es/cuenta/verificar-correo";
  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(15);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioTokensVerificacionFalso tokens;
  private EnviadorDeCorreoFalso enviador;
  private LimitadorDeIntentosFalso limitadorDeIntentos;

  private ReenviarVerificacion crear() {
    usuarios = new RepositorioUsuariosFalso();
    tokens = new RepositorioTokensVerificacionFalso();
    enviador = new EnviadorDeCorreoFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    return new ReenviarVerificacion(
        usuarios,
        tokens,
        enviador,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA),
        VIGENCIA_TOKEN,
        URL_BASE,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA);
  }

  private void conUsuario(String correo, boolean verificado) {
    Usuario usuario = Usuario.crear(new CorreoElectronico(correo), "hash", Rol.CLIENTE, AHORA);
    if (verificado) {
      usuario.verificarCorreo(AHORA);
    }
    usuarios.conUsuario(usuario);
  }

  @Test
  @DisplayName("una cuenta sin verificar recibe un token nuevo y su enlace")
  void unaCuentaSinVerificarRecibeSuEnlace() {
    ReenviarVerificacion caso = crear();
    conUsuario("cliente@tecnosport.co", false);

    caso.ejecutar(new ReenviarVerificacionComando("cliente@tecnosport.co"));

    assertEquals(1, tokens.todos().size());
    assertEquals(1, enviador.enviados().size());
    EnviadorDeCorreoFalso.CorreoEnviado correo = enviador.enviados().get(0);
    assertEquals(new CorreoElectronico("cliente@tecnosport.co"), correo.destinatario());
    assertTrue(correo.cuerpoHtml().contains(URL_BASE));
    assertTrue(correo.cuerpoHtml().contains(tokens.todos().get(0).id().toString()));
  }

  /**
   * Los tres desenlaces silenciosos tienen que ser indistinguibles entre sí, y por eso van los tres
   * escritos: si mañana alguien hace que uno responda distinto, esto se convierte en un oráculo
   * para averiguar qué correos tienen cuenta aquí y en qué estado (OWASP,
   * docs/08-seguridad-legal.md).
   */
  @Test
  @DisplayName("una cuenta ya verificada no recibe nada, igual que una que no existe")
  void unaCuentaYaVerificadaNoRecibeNada() {
    ReenviarVerificacion caso = crear();
    conUsuario("cliente@tecnosport.co", true);

    caso.ejecutar(new ReenviarVerificacionComando("cliente@tecnosport.co"));

    assertEquals(0, tokens.todos().size(), "ni siquiera se quema un token");
    assertEquals(0, enviador.enviados().size());
  }

  @Test
  @DisplayName("un correo sin cuenta no hace nada")
  void unCorreoSinCuentaNoHaceNada() {
    ReenviarVerificacion caso = crear();

    caso.ejecutar(new ReenviarVerificacionComando("no-existe@tecnosport.co"));

    assertEquals(0, tokens.todos().size());
    assertEquals(0, enviador.enviados().size());
  }

  @Test
  @DisplayName("un fallo al encolar no delata que la cuenta existe")
  void unFalloAlEncolarNoDelataQueLaCuentaExiste() {
    ReenviarVerificacion caso = crear();
    conUsuario("cliente@tecnosport.co", false);
    enviador.hazQueFalle();

    caso.ejecutar(new ReenviarVerificacionComando("cliente@tecnosport.co"));

    assertEquals(1, tokens.todos().size(), "el token sí se creó");
    assertEquals(0, enviador.enviados().size());
  }

  /**
   * El límite va antes de buscar la cuenta, así que también salta con un correo que no existe. Sin
   * eso, esto es una forma cómoda de llenarle la casilla a cualquiera desde un formulario público.
   */
  @Test
  @DisplayName("el límite por cuenta salta aunque el correo no exista")
  void elLimiteSaltaAunSinCuenta() {
    ReenviarVerificacion caso = crear();
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso.ejecutar(new ReenviarVerificacionComando("no-existe@tecnosport.co")));
  }
}
