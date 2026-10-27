package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.CorreoSinVerificarException;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IniciarSesionTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);
  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(15);
  private static final int MAXIMO_INTENTOS_POR_CUENTA_SOLA = 100;
  private static final CorreoElectronico CORREO = new CorreoElectronico("admin@tecnosport.co");
  private static final String IP = "203.0.113.5";

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSesionesFalso sesiones;
  private CodificadorDeClavesFalso codificador;
  private LimitadorDeIntentosFalso limitadorDeIntentos;
  private RepositorioSegundosFactoresFalso factores = new RepositorioSegundosFactoresFalso();
  private RepositorioDesafiosSegundoFactorFalso desafios =
      new RepositorioDesafiosSegundoFactorFalso();
  private static final Duration VIGENCIA_DESAFIO = Duration.ofMinutes(5);

  private static TokensDeSesion abierta(ResultadoInicioSesion resultado) {
    return ((ResultadoInicioSesion.SesionAbierta) resultado).tokens();
  }

  private IniciarSesion crear() {
    return crear(new LimitadorDeIntentosFalso());
  }

  private IniciarSesion crear(LimitadorDeIntentos limitador) {
    usuarios = new RepositorioUsuariosFalso();
    sesiones = new RepositorioSesionesFalso();
    codificador = new CodificadorDeClavesFalso();
    factores = new RepositorioSegundosFactoresFalso();
    desafios = new RepositorioDesafiosSegundoFactorFalso();
    limitadorDeIntentos =
        limitador instanceof LimitadorDeIntentosFalso falso
            ? falso
            : new LimitadorDeIntentosFalso();
    return new IniciarSesion(
        usuarios,
        sesiones,
        codificador,
        new GeneradorDeTokensFalso(),
        new RelojFalso(AHORA),
        VIGENCIA_REFRESCO,
        limitador,
        MAXIMO_INTENTOS_POR_CUENTA,
        MAXIMO_INTENTOS_POR_CUENTA_SOLA,
        VENTANA_INTENTOS_POR_CUENTA,
        factores,
        desafios,
        VIGENCIA_DESAFIO);
  }

  private void conUsuarioAdmin(String claveTextoPlano) {
    Usuario usuario =
        Usuario.crear(CORREO, codificador.codificar(claveTextoPlano), Rol.ADMIN, AHORA);
    usuario.verificarCorreo(AHORA);
    usuarios.conUsuario(usuario);
  }

  @Test
  void loginExitosoDevuelveTokensYCreaUnaSesion() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");

    TokensDeSesion tokens =
        abierta(
            caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP)));

    assertEquals(Rol.ADMIN, tokens.rol());
    assertEquals(1, sesiones.todas().size());
    assertEquals(tokens.refreshTokenId(), sesiones.todas().get(0).id());
  }

  @Test
  void unLoginExitosoOlvidaElConteoDeIntentos() {
    // El límite cuenta intentos fallidos SEGUIDOS, no intentos a secas. `permitir` cuenta antes
    // de verificar —contar y comprobar en una sola sentencia es lo que cierra la carrera—, así
    // que sin este olvido entrar, salir y volver a entrar agota los cinco sin un solo error.
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");

    caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP));

    assertEquals(
        java.util.List.of(
            "cuenta-ip:iniciar-sesion:admin@tecnosport.co:" + IP,
            "cuenta:iniciar-sesion:admin@tecnosport.co"),
        limitadorDeIntentos.olvidadas());
  }

  @Test
  void unaClaveEquivocadaNoOlvidaNada() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");

    assertThrows(
        CredencialesInvalidasException.class,
        () ->
            caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-incorrecta", IP)));

    assertEquals(java.util.List.of(), limitadorDeIntentos.olvidadas());
  }

  @Test
  void correoInexistenteLanzaCredencialesInvalidas() {
    IniciarSesion caso = crear();

    assertThrows(
        CredencialesInvalidasException.class,
        () -> caso.ejecutar(new IniciarSesionComando("no-existe@tecnosport.co", "cualquiera", IP)));
  }

  @Test
  void claveIncorrectaLanzaCredencialesInvalidas() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");

    assertThrows(
        CredencialesInvalidasException.class,
        () ->
            caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-incorrecta", IP)));
  }

  @Test
  void correoSinVerificarLanzaCorreoSinVerificar() {
    IniciarSesion caso = crear();
    Usuario usuario =
        Usuario.crear(CORREO, codificador.codificar("clave-correcta"), Rol.CLIENTE, AHORA);
    usuarios.conUsuario(usuario);

    assertThrows(
        CorreoSinVerificarException.class,
        () -> caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP)));
  }

  @Test
  void excederElLimiteDeIntentosPorCuentaLanzaLimiteDeIntentosExcedido() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP)));
  }

  /** ADR-0074: una cuenta que solo entra con Google no tiene clave que adivinar. */
  @Test
  void unaCuentaSinClaveNoEntraConNingunaClave() {
    IniciarSesion caso = crear();
    usuarios.conUsuario(Usuario.crearConGoogle(CORREO, "sub-1", AHORA));

    assertThrows(
        CredencialesInvalidasException.class,
        () -> caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "", IP)));
  }

  /**
   * El correo del administrador es público. Con el cierre por cuenta sola, cinco intentos desde
   * cualquier sitio dejaban fuera al titular, clave correcta incluida.
   */
  @Test
  void losFallosDesdeOtraIpNoCierranLaCuentaAlTitular() {
    IniciarSesion caso = crear(new LimitadorQueCuenta());
    conUsuarioAdmin("clave-correcta");
    for (int i = 0; i < MAXIMO_INTENTOS_POR_CUENTA * 3; i++) {
      assertThrows(
          RuntimeException.class,
          () ->
              caso.ejecutar(
                  new IniciarSesionComando("admin@tecnosport.co", "adivinanza", "198.51.100.66")));
    }

    TokensDeSesion tokens =
        abierta(
            caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP)));

    assertEquals(Rol.ADMIN, tokens.rol());
  }

  @Test
  void elOrigenQueFallaSeCierraASiMismo() {
    IniciarSesion caso = crear(new LimitadorQueCuenta());
    conUsuarioAdmin("clave-correcta");
    for (int i = 0; i < MAXIMO_INTENTOS_POR_CUENTA; i++) {
      assertThrows(
          CredencialesInvalidasException.class,
          () ->
              caso.ejecutar(
                  new IniciarSesionComando("admin@tecnosport.co", "adivinanza", "198.51.100.66")));
    }

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () ->
            caso.ejecutar(
                new IniciarSesionComando(
                    "admin@tecnosport.co", "clave-correcta", "198.51.100.66")));
  }

  /** La adivinanza repartida entre muchos orígenes sí la frena el tope por cuenta sola. */
  @Test
  void muchosOrigenesAgotanElTopePorCuentaSola() {
    IniciarSesion caso = crear(new LimitadorQueCuenta());
    conUsuarioAdmin("clave-correcta");
    for (int i = 0; i < MAXIMO_INTENTOS_POR_CUENTA_SOLA; i++) {
      String origen = "198.51.100." + (i % 250);
      assertThrows(
          RuntimeException.class,
          () ->
              caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "adivinanza", origen)));
    }

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () ->
            caso.ejecutar(
                new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", "192.0.2.1")));
  }

  /** Cuenta de verdad por llave, sin ventana: dentro de una prueba no pasa el tiempo. */
  private static final class LimitadorQueCuenta implements LimitadorDeIntentos {
    private final Map<String, Integer> conteos = new HashMap<>();

    @Override
    public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
      return conteos.merge(clave, 1, Integer::sum) <= maximoIntentos;
    }

    @Override
    public void olvidar(String clave) {
      conteos.remove(clave);
    }
  }

  /**
   * Sin cuenta no se calculaba ningún hash, y la respuesta llegaba antes: el mismo error con otro
   * tiempo dice qué correos existen.
   */
  @Test
  void sinCuentaTambienSeCalculaUnHash() {
    crear();
    VerificacionesContadas contadas = new VerificacionesContadas();
    IniciarSesion conConteo =
        new IniciarSesion(
            usuarios,
            sesiones,
            contadas,
            new GeneradorDeTokensFalso(),
            new RelojFalso(AHORA),
            VIGENCIA_REFRESCO,
            limitadorDeIntentos,
            MAXIMO_INTENTOS_POR_CUENTA,
            MAXIMO_INTENTOS_POR_CUENTA_SOLA,
            VENTANA_INTENTOS_POR_CUENTA,
            factores,
            desafios,
            VIGENCIA_DESAFIO);
    usuarios.conUsuario(Usuario.crearConGoogle(CORREO, "sub-1", AHORA));

    assertThrows(
        CredencialesInvalidasException.class,
        () -> conConteo.ejecutar(new IniciarSesionComando("no-existe@tecnosport.co", "x", IP)));
    assertThrows(
        CredencialesInvalidasException.class,
        () -> conConteo.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "x", IP)));

    assertEquals(2, contadas.verificaciones);
  }

  private static final class VerificacionesContadas implements CodificadorDeClaves {
    private final CodificadorDeClavesFalso real = new CodificadorDeClavesFalso();
    private int verificaciones;

    @Override
    public String codificar(String claveTextoPlano) {
      return real.codificar(claveTextoPlano);
    }

    @Override
    public boolean verificar(String claveTextoPlano, String claveHash) {
      verificaciones++;
      return real.verificar(claveTextoPlano, claveHash);
    }
  }

  private Usuario adminConFactorActivo() {
    Usuario usuario = usuarios.buscarPorCorreo(CORREO).orElseThrow();
    SegundoFactor factor = SegundoFactor.iniciar(usuario.id(), AHORA);
    factor.confirmar(
        Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    factores.con(factor);
    return usuario;
  }

  @Test
  void conElSegundoFactorActivoLaClaveDejaUnDesafioYNingunaSesion() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");
    Usuario usuario = adminConFactorActivo();

    ResultadoInicioSesion resultado =
        caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP));

    ResultadoInicioSesion.SegundoFactorRequerido requerido =
        assertInstanceOf(ResultadoInicioSesion.SegundoFactorRequerido.class, resultado);
    assertEquals(AHORA.plus(VIGENCIA_DESAFIO), requerido.venceEn());
    assertTrue(sesiones.todas().isEmpty());
    DesafioSegundoFactor desafio = desafios.todos().get(0);
    assertEquals(requerido.desafio(), desafio.id());
    assertEquals(usuario.id(), desafio.usuarioId());
  }

  @Test
  void unFactorPendienteNoPideElSegundoPaso() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");
    Usuario usuario = usuarios.buscarPorCorreo(CORREO).orElseThrow();
    factores.con(SegundoFactor.iniciar(usuario.id(), AHORA));

    assertInstanceOf(
        ResultadoInicioSesion.SesionAbierta.class,
        caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta", IP)));
    assertTrue(desafios.todos().isEmpty());
  }

  /**
   * ADR-0096, decisión 11: recuperar la clave por correo no desactiva el segundo factor. El correo
   * no es un segundo factor del correo.
   */
  @Test
  void recuperarLaClaveNoDesactivaElSegundoFactor() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-vieja");
    Usuario usuario = adminConFactorActivo();
    RepositorioTokensRecuperacionFalso tokens = new RepositorioTokensRecuperacionFalso();
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(usuario.id(), AHORA, Duration.ofMinutes(30));
    tokens.guardar(token);
    new ConfirmarRecuperacion(tokens, usuarios, sesiones, codificador, new RelojFalso(AHORA))
        .ejecutar(new ConfirmarRecuperacionComando(token.id().toString(), "clave-nueva"));

    assertInstanceOf(
        ResultadoInicioSesion.SegundoFactorRequerido.class,
        caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-nueva", IP)));
  }
}
