package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.CodigoDeRespaldo;
import co.tecnosport.api.domain.usuario.CodigoSegundoFactorInvalidoException;
import co.tecnosport.api.domain.usuario.PoliticaDeSegundoFactor;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactorNoActivoException;
import co.tecnosport.api.domain.usuario.SegundoFactorObligatorioException;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consultar, regenerar los códigos y desactivar el segundo factor (ADR-0096). */
class GestionDelSegundoFactorTest {

  private static final Instant ACTIVADO = Instant.parse("2026-10-27T11:00:00Z");
  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSegundosFactoresFalso factores;
  private RepositorioSesionesFalso sesiones;
  private EnviadorDeCorreoFalso correos;
  private LimitadorDeIntentosFalso limitador;
  private Usuario admin;
  private SegundoFactor factor;
  private List<CodigoDeRespaldo> respaldos;

  @BeforeEach
  void preparar() {
    usuarios = new RepositorioUsuariosFalso();
    factores = new RepositorioSegundosFactoresFalso();
    sesiones = new RepositorioSesionesFalso();
    correos = new EnviadorDeCorreoFalso();
    limitador = new LimitadorDeIntentosFalso();
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

  private String codigoActual() {
    return Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA));
  }

  private SesionRefresco sesionAbierta() {
    SesionRefresco sesion =
        SesionRefresco.crear(admin.id(), UUID.randomUUID(), ACTIVADO, Duration.ofDays(30));
    sesiones.guardar(sesion);
    return sesion;
  }

  private RegenerarCodigosDeRespaldo regenerar() {
    return new RegenerarCodigosDeRespaldo(
        usuarios,
        factores,
        sesiones,
        new CodificadorDeClavesFalso(),
        new GeneradorDeTokensFalso(),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA),
        Duration.ofDays(30),
        limitador,
        5,
        Duration.ofMinutes(15));
  }

  private DesactivarSegundoFactor desactivar(boolean exigido) {
    return new DesactivarSegundoFactor(
        usuarios,
        factores,
        sesiones,
        new CodificadorDeClavesFalso(),
        new GeneradorDeTokensFalso(),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA),
        Duration.ofDays(30),
        limitador,
        5,
        Duration.ofMinutes(15),
        new PoliticaDeSegundoFactor(exigido));
  }

  @Test
  void consultarDiceSiEstaActivoCuantosQuedanYSiEsObligatorio() {
    ConsultarSegundoFactor.Estado estado =
        new ConsultarSegundoFactor(usuarios, factores, new PoliticaDeSegundoFactor(true))
            .ejecutar(admin.id());

    assertTrue(estado.activo());
    assertEquals(ACTIVADO, estado.activadoEn());
    assertEquals(10, estado.codigosDeRespaldoRestantes());
    assertTrue(estado.obligatorio());
  }

  @Test
  void unaActivacionPendienteSeConsultaComoInactiva() {
    factores.con(SegundoFactor.iniciar(admin.id(), AHORA));

    ConsultarSegundoFactor.Estado estado =
        new ConsultarSegundoFactor(usuarios, factores, new PoliticaDeSegundoFactor(false))
            .ejecutar(admin.id());

    assertFalse(estado.activo());
    assertNull(estado.activadoEn());
    assertEquals(0, estado.codigosDeRespaldoRestantes());
    assertFalse(estado.obligatorio());
  }

  @Test
  void regenerarInvalidaLosViejosRevocaLasSesionesYAvisa() {
    SesionRefresco vieja = sesionAbierta();

    RegenerarCodigosDeRespaldo.CodigosRegenerados regenerados =
        regenerar()
            .ejecutar(new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", codigoActual()));

    assertEquals(10, regenerados.codigosDeRespaldo().size());
    SegundoFactor guardado = factores.de(admin.id()).orElseThrow();
    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> guardado.verificar(respaldos.get(0).mostrado(), AHORA));
    assertTrue(sesiones.buscarPorId(vieja.id()).orElseThrow().revocadoEn().isPresent());
    assertTrue(
        sesiones
            .buscarPorId(regenerados.sesion().refreshTokenId())
            .orElseThrow()
            .estaVigente(AHORA));
    assertEquals(
        "[usuario.segundo_factor.respaldo_regenerado.asunto]", correos.enviados().get(0).asunto());
  }

  @Test
  void regenerarPideLaClaveYElCodigo() {
    assertThrows(
        CredencialesInvalidasException.class,
        () ->
            regenerar()
                .ejecutar(
                    new ClaveYCodigoSegundoFactorComando(admin.id(), "otra", codigoActual())));
    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () ->
            regenerar()
                .ejecutar(new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", "000000")));
    assertEquals(10, factores.de(admin.id()).orElseThrow().restantes());
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void desactivarBorraElFactorRevocaLasSesionesYAvisa() {
    SesionRefresco vieja = sesionAbierta();

    TokensDeSesion tokens =
        desactivar(false)
            .ejecutar(new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", codigoActual()));

    assertTrue(factores.de(admin.id()).isEmpty());
    assertFalse(tokens.segundoFactorPorActivar());
    assertTrue(sesiones.buscarPorId(vieja.id()).orElseThrow().revocadoEn().isPresent());
    assertEquals("[usuario.segundo_factor.desactivado.asunto]", correos.enviados().get(0).asunto());
  }

  @Test
  void conUnCodigoDeRespaldoTambienSeDesactiva() {
    desactivar(false)
        .ejecutar(
            new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", respaldos.get(2).mostrado()));

    assertTrue(factores.de(admin.id()).isEmpty());
  }

  @Test
  void siElPanelLoExigeNoSeDesactiva() {
    assertThrows(
        SegundoFactorObligatorioException.class,
        () ->
            desactivar(true)
                .ejecutar(
                    new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", codigoActual())));
    assertTrue(factores.de(admin.id()).orElseThrow().activo());
  }

  @Test
  void sinFactorActivoNoHayNadaQueDesactivarNiRegenerar() {
    factores.eliminar(admin.id());

    assertThrows(
        SegundoFactorNoActivoException.class,
        () ->
            desactivar(false)
                .ejecutar(new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", "123456")));
    assertThrows(
        SegundoFactorNoActivoException.class,
        () ->
            regenerar()
                .ejecutar(new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", "123456")));
  }

  @Test
  void conUnCodigoEquivocadoNoSeDesactiva() {
    String malo = Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA) + 10);

    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () ->
            desactivar(false)
                .ejecutar(new ClaveYCodigoSegundoFactorComando(admin.id(), "clave", malo)));
    assertTrue(factores.de(admin.id()).orElseThrow().activo());
    assertTrue(limitador.olvidadas().isEmpty());
  }
}
