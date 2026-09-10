package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.legal.RepositorioAutorizacionesFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.legal.AutorizacionRequeridaException;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
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
  private static final String VERSION_POLITICA = "2026-09-07";
  private static final String IP = "190.24.10.5";

  private RepositorioUsuariosFalso usuarios;
  private RepositorioTokensVerificacionFalso tokens;
  private EnviadorDeCorreoFalso enviador;
  private LimitadorDeIntentosFalso limitadorDeIntentos;
  private RepositorioAutorizacionesFalso autorizaciones;

  private RegistrarUsuario crear() {
    usuarios = new RepositorioUsuariosFalso();
    tokens = new RepositorioTokensVerificacionFalso();
    enviador = new EnviadorDeCorreoFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    autorizaciones = new RepositorioAutorizacionesFalso();
    return new RegistrarUsuario(
        usuarios,
        tokens,
        new CodificadorDeClavesFalso(),
        enviador,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA),
        VIGENCIA_TOKEN,
        URL_BASE,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA,
        autorizaciones,
        VERSION_POLITICA);
  }

  /** Todo comando de estas pruebas autoriza, salvo el que prueba justamente lo contrario. */
  private RegistrarUsuarioComando comando(String correo, String clave) {
    return new RegistrarUsuarioComando(correo, clave, true, IP);
  }

  @Test
  void registrarCreaUnClienteSinVerificar() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura"));

    Usuario usuario =
        usuarios.buscarPorCorreo(new CorreoElectronico("cliente@tecnosport.co")).orElseThrow();
    assertEquals(Rol.CLIENTE, usuario.rol());
    assertFalse(usuario.correoVerificado());
  }

  @Test
  void registrarCreaUnTokenDeVerificacionYEnviaElCorreo() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura"));

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
    caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura"));

    assertThrows(
        CorreoYaRegistradoException.class,
        () -> caso.ejecutar(comando("cliente@tecnosport.co", "otra-clave")));
  }

  @Test
  void excederElLimiteDeIntentosPorCuentaLanzaLimiteDeIntentosExcedido() {
    RegistrarUsuario caso = crear();
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura")));
  }

  @Test
  void registrarDejaLaConstanciaDeAutorizacion() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura"));

    AutorizacionDatos constancia = autorizaciones.todas().getFirst();
    assertEquals(new CorreoElectronico("cliente@tecnosport.co"), constancia.correo());
    assertEquals(OrigenAutorizacion.REGISTRO, constancia.origen());
    assertEquals(IP, constancia.direccionIp());
    assertEquals(AHORA, constancia.otorgadaEn());
  }

  /**
   * La versión la pone el servidor, no el comando: el comprador no puede declarar qué texto aceptó
   * (regla dura #7). Si esto se rompiera, bastaría manipular la petición para dejar constancia de
   * haber aceptado una versión que nunca se mostró.
   */
  @Test
  void laVersionDeLaPoliticaLaPoneElServidor() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura"));

    assertEquals(VERSION_POLITICA, autorizaciones.todas().getFirst().versionPolitica());
  }

  @Test
  void laConstanciaQuedaAtadaAlUsuarioRecienCreado() {
    RegistrarUsuario caso = crear();

    caso.ejecutar(comando("cliente@tecnosport.co", "clave-segura"));

    Usuario usuario =
        usuarios.buscarPorCorreo(new CorreoElectronico("cliente@tecnosport.co")).orElseThrow();
    assertEquals(
        java.util.Optional.of(usuario.id()), autorizaciones.todas().getFirst().usuarioId());
  }

  @Test
  void sinAutorizarNoHayRegistro() {
    RegistrarUsuario caso = crear();

    assertThrows(
        AutorizacionRequeridaException.class,
        () ->
            caso.ejecutar(
                new RegistrarUsuarioComando("cliente@tecnosport.co", "clave-segura", false, IP)));
  }

  /**
   * Lo que de verdad importa del caso anterior: que no quede una cuenta a medias. Sin autorización
   * no se guarda el usuario, ni su token, ni se manda el correo.
   */
  @Test
  void sinAutorizarNoQuedaNadaGuardado() {
    RegistrarUsuario caso = crear();

    assertThrows(
        AutorizacionRequeridaException.class,
        () ->
            caso.ejecutar(
                new RegistrarUsuarioComando("cliente@tecnosport.co", "clave-segura", false, IP)));

    assertTrue(usuarios.buscarPorCorreo(new CorreoElectronico("cliente@tecnosport.co")).isEmpty());
    assertTrue(tokens.todos().isEmpty());
    assertTrue(enviador.enviados().isEmpty());
    assertTrue(autorizaciones.todas().isEmpty());
  }
}
