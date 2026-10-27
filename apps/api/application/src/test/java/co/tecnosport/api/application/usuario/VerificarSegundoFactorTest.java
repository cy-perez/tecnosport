package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.CodigoDeRespaldo;
import co.tecnosport.api.domain.usuario.CodigoSegundoFactorInvalidoException;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactorInvalidoException;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificarSegundoFactorTest {

  private static final Instant ACTIVADO = Instant.parse("2026-10-27T11:00:00Z");
  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");
  private static final String IP = "203.0.113.5";
  private static final int POR_DESAFIO = 5;
  private static final int POR_CUENTA_E_IP = 5;
  private static final int POR_CUENTA = 10;

  /** Cuenta de verdad, como el adaptador: cada permitir suma, y olvidar borra. */
  private static final class LimitadorQueCuenta implements LimitadorDeIntentos {
    final Map<String, Integer> conteos = new HashMap<>();
    final List<String> olvidadas = new ArrayList<>();

    @Override
    public boolean permitir(String clave, int maximo, Duration ventana, Instant ahora) {
      return conteos.merge(clave, 1, Integer::sum) <= maximo;
    }

    @Override
    public void olvidar(String clave) {
      olvidadas.add(clave);
      conteos.remove(clave);
    }
  }

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSegundosFactoresFalso factores;
  private RepositorioDesafiosSegundoFactorFalso desafios;
  private RepositorioSesionesFalso sesiones;
  private EnviadorDeCorreoFalso correos;
  private LimitadorQueCuenta limitador;
  private Usuario admin;
  private SegundoFactor factor;
  private List<CodigoDeRespaldo> respaldos;

  private VerificarSegundoFactor caso(Instant ahora) {
    return new VerificarSegundoFactor(
        desafios,
        factores,
        usuarios,
        sesiones,
        new GeneradorDeTokensFalso(),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(ahora),
        Duration.ofDays(30),
        limitador,
        POR_DESAFIO,
        POR_CUENTA_E_IP,
        POR_CUENTA,
        Duration.ofMinutes(15));
  }

  @BeforeEach
  void preparar() {
    usuarios = new RepositorioUsuariosFalso();
    factores = new RepositorioSegundosFactoresFalso();
    desafios = new RepositorioDesafiosSegundoFactorFalso();
    sesiones = new RepositorioSesionesFalso();
    correos = new EnviadorDeCorreoFalso();
    limitador = new LimitadorQueCuenta();
    admin =
        Usuario.crear(
            new CorreoElectronico("admin@tecnosport.co"), "hash:clave", Rol.ADMIN, ACTIVADO);
    admin.verificarCorreo(ACTIVADO);
    usuarios.conUsuario(admin);
    factor = SegundoFactor.iniciar(admin.id(), ACTIVADO);
    respaldos =
        factor.confirmar(
            Totp.codigoEn(factor.clave(), Totp.pasoDe(ACTIVADO)), ACTIVADO, Duration.ofMinutes(15));
    factores.con(factor);
  }

  private DesafioSegundoFactor desafio() {
    DesafioSegundoFactor desafio =
        DesafioSegundoFactor.crear(admin.id(), AHORA, Duration.ofMinutes(5));
    desafios.guardar(desafio);
    return desafio;
  }

  private String codigoActual() {
    return Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA));
  }

  private String codigoMalo() {
    return Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA) + 10);
  }

  private TokensDeSesion verificar(DesafioSegundoFactor desafio, String codigo, String ip) {
    return caso(AHORA)
        .ejecutar(new VerificarSegundoFactorComando(desafio.id().toString(), codigo, ip));
  }

  @Test
  void elCodigoCorrectoAbreLaSesionYConsumeElDesafio() {
    DesafioSegundoFactor desafio = desafio();

    TokensDeSesion tokens = verificar(desafio, codigoActual(), IP);

    assertEquals(admin.id(), tokens.usuarioId());
    assertTrue(sesiones.buscarPorId(tokens.refreshTokenId()).orElseThrow().estaVigente(AHORA));
    assertTrue(desafios.buscarParaConsumir(desafio.id()).orElseThrow().usadoEn().isPresent());
    assertEquals(
        Totp.pasoDe(AHORA), factores.de(admin.id()).orElseThrow().ultimoPasoUsado().orElseThrow());
    assertTrue(correos.enviados().isEmpty());
    assertThrows(
        DesafioSegundoFactorInvalidoException.class, () -> verificar(desafio, codigoActual(), IP));
  }

  @Test
  void elAciertoOlvidaLasDosLlavesDeCuentaYNoLaDelDesafio() {
    verificar(desafio(), codigoActual(), IP);

    assertEquals(
        List.of(
            "cuenta-ip:segundo-factor:" + admin.id() + ":" + IP,
            "cuenta:segundo-factor:" + admin.id()),
        limitador.olvidadas);
  }

  @Test
  void elMismoCodigoNoSirveEnOtroDesafio() {
    verificar(desafio(), codigoActual(), IP);
    DesafioSegundoFactor otro = desafio();

    assertThrows(
        CodigoSegundoFactorInvalidoException.class, () -> verificar(otro, codigoActual(), IP));
  }

  @Test
  void unCodigoEquivocadoDejaElDesafioVivo() {
    DesafioSegundoFactor desafio = desafio();

    assertThrows(
        CodigoSegundoFactorInvalidoException.class, () -> verificar(desafio, codigoMalo(), IP));
    assertEquals(admin.id(), verificar(desafio, codigoActual(), IP).usuarioId());
  }

  @Test
  void cincoFallosMatanElDesafio() {
    DesafioSegundoFactor desafio = desafio();
    for (int i = 0; i < POR_DESAFIO; i++) {
      // Desde IPs distintas, para que no sea el tope de cuenta e IP el que corta.
      String ip = "198.51.100." + i;
      assertThrows(
          CodigoSegundoFactorInvalidoException.class, () -> verificar(desafio, codigoMalo(), ip));
    }

    assertThrows(
        DesafioSegundoFactorInvalidoException.class,
        () -> verificar(desafio, codigoActual(), "192.0.2.1"));
  }

  @Test
  void elTopeDeCuentaEIpCierraAlOrigenYNoALaCuenta() {
    DesafioSegundoFactor primero = desafio();
    DesafioSegundoFactor segundo = desafio();
    for (int i = 0; i < 3; i++) {
      assertThrows(
          CodigoSegundoFactorInvalidoException.class, () -> verificar(primero, codigoMalo(), IP));
    }
    for (int i = 0; i < 2; i++) {
      assertThrows(
          CodigoSegundoFactorInvalidoException.class, () -> verificar(segundo, codigoMalo(), IP));
    }

    assertThrows(
        LimiteDeIntentosExcedidoException.class, () -> verificar(segundo, codigoActual(), IP));
    assertEquals(admin.id(), verificar(segundo, codigoActual(), "192.0.2.1").usuarioId());
  }

  @Test
  void elTopePorCuentaSolaFrenaElAtaqueRepartido() {
    for (int i = 0; i < POR_CUENTA; i++) {
      DesafioSegundoFactor desafio = desafio();
      String ip = "198.51.100." + i;
      assertThrows(
          CodigoSegundoFactorInvalidoException.class, () -> verificar(desafio, codigoMalo(), ip));
    }
    DesafioSegundoFactor ultimo = desafio();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> verificar(ultimo, codigoActual(), "192.0.2.1"));
  }

  @Test
  void unDesafioVencidoNoSirve() {
    DesafioSegundoFactor desafio = desafio();

    assertThrows(
        DesafioSegundoFactorInvalidoException.class,
        () ->
            caso(AHORA.plus(Duration.ofMinutes(5)))
                .ejecutar(
                    new VerificarSegundoFactorComando(
                        desafio.id().toString(), codigoActual(), IP)));
  }

  @Test
  void unDesafioQueNoEsUnUuidOQueNoExisteNoSirve() {
    assertThrows(
        DesafioSegundoFactorInvalidoException.class,
        () -> caso(AHORA).ejecutar(new VerificarSegundoFactorComando("basura", "123456", IP)));
    assertThrows(
        DesafioSegundoFactorInvalidoException.class,
        () -> caso(AHORA).ejecutar(new VerificarSegundoFactorComando(null, "123456", IP)));
    assertThrows(
        DesafioSegundoFactorInvalidoException.class,
        () ->
            caso(AHORA)
                .ejecutar(
                    new VerificarSegundoFactorComando(
                        java.util.UUID.randomUUID().toString(), "123456", IP)));
    assertTrue(limitador.conteos.isEmpty());
  }

  @Test
  void unCodigoDeRespaldoSirveUnaVezYAvisaConLosQueQuedan() {
    String respaldo = respaldos.get(0).mostrado();

    verificar(desafio(), respaldo, IP);

    assertEquals(1, correos.enviados().size());
    assertEquals(
        "[usuario.segundo_factor.respaldo_usado.cuerpo|9]", correos.enviados().get(0).cuerpoHtml());
    DesafioSegundoFactor otro = desafio();
    assertThrows(CodigoSegundoFactorInvalidoException.class, () -> verificar(otro, respaldo, IP));
  }

  @Test
  void siElFactorSeDesactivoEntreTantoElDesafioNoSirve() {
    DesafioSegundoFactor desafio = desafio();
    factores.eliminar(admin.id());

    assertThrows(
        DesafioSegundoFactorInvalidoException.class, () -> verificar(desafio, codigoActual(), IP));
  }
}
