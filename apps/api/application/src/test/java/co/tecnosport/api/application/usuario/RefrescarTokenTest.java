package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.PoliticaDeSegundoFactor;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefrescarTokenTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSesionesFalso sesiones;
  private Usuario usuario;

  private RefrescarToken crear() {
    usuarios = new RepositorioUsuariosFalso();
    sesiones = new RepositorioSesionesFalso();
    usuario = Usuario.crear(new CorreoElectronico("admin@tecnosport.co"), "hash", Rol.ADMIN, AHORA);
    usuarios.conUsuario(usuario);
    return new RefrescarToken(
        sesiones,
        usuarios,
        new GeneradorDeTokensFalso(),
        new RelojFalso(AHORA),
        VIGENCIA_REFRESCO,
        factores,
        new PoliticaDeSegundoFactor(exigido));
  }

  private RepositorioSegundosFactoresFalso factores = new RepositorioSegundosFactoresFalso();
  private boolean exigido;

  /**
   * ADR-0096. Con la bandera encendida, el refresco de un ADMIN sin factor sale restringido —el
   * alcance se recalcula en cada emisión, así que la bandera alcanza a las sesiones ya abiertas—, y
   * en cuanto activa el factor sale completo.
   */
  @Test
  void elRefrescoRecalculaElAlcance() {
    exigido = true;
    RefrescarToken caso = crear();
    SesionRefresco original = sesionVigente();

    TokensDeSesion restringida = caso.ejecutar(new RefrescarTokenComando(original.id()));

    assertTrue(restringida.segundoFactorPorActivar());
    assertTrue(restringida.accessToken().endsWith(":SOLO_ACTIVAR_SEGUNDO_FACTOR"));

    SegundoFactor factor = SegundoFactor.iniciar(usuario.id(), AHORA);
    factor.confirmar(
        Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    factores.con(factor);
    TokensDeSesion completa =
        caso.ejecutar(new RefrescarTokenComando(restringida.refreshTokenId()));

    assertFalse(completa.segundoFactorPorActivar());
    assertTrue(completa.accessToken().endsWith(":COMPLETO"));
  }

  @Test
  void sinLaBanderaNadieSaleRestringido() {
    RefrescarToken caso = crear();
    assertFalse(
        caso.ejecutar(new RefrescarTokenComando(sesionVigente().id())).segundoFactorPorActivar());
  }

  private SesionRefresco sesionVigente() {
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), AHORA, VIGENCIA_REFRESCO);
    sesiones.guardar(sesion);
    return sesion;
  }

  @Test
  void refrescarUnaSesionValidaRotaYDevuelveTokensNuevos() {
    RefrescarToken caso = crear();
    SesionRefresco original = sesionVigente();

    TokensDeSesion tokens = caso.ejecutar(new RefrescarTokenComando(original.id()));

    assertNotEquals(original.id(), tokens.refreshTokenId());
    assertEquals(Rol.ADMIN, tokens.rol());
    SesionRefresco viejaActualizada = sesiones.buscarPorId(original.id()).orElseThrow();
    assertTrue(viejaActualizada.usadoEn().isPresent());
    SesionRefresco nueva = sesiones.buscarPorId(tokens.refreshTokenId()).orElseThrow();
    assertEquals(original.familiaId(), nueva.familiaId());
  }

  @Test
  void refrescarUnaSesionYaUsadaRevocaLaFamiliaYLanzaComprometida() {
    RefrescarToken caso = crear();
    SesionRefresco original = sesionVigente();
    TokensDeSesion primerRefresco = caso.ejecutar(new RefrescarTokenComando(original.id()));

    assertThrows(
        SesionDeRefrescoComprometidaException.class,
        () -> caso.ejecutar(new RefrescarTokenComando(original.id())));

    SesionRefresco sesionNueva =
        sesiones.buscarPorId(primerRefresco.refreshTokenId()).orElseThrow();
    assertTrue(sesionNueva.revocadoEn().isPresent());
  }

  @Test
  void refrescarUnaSesionVencidaLanzaInvalidaSinRevocarLaFamilia() {
    RefrescarToken caso = crear();
    SesionRefresco vencida =
        SesionRefresco.crear(
            usuario.id(),
            UUID.randomUUID(),
            AHORA.minus(VIGENCIA_REFRESCO.plusDays(1)),
            VIGENCIA_REFRESCO);
    sesiones.guardar(vencida);

    assertThrows(
        SesionDeRefrescoInvalidaException.class,
        () -> caso.ejecutar(new RefrescarTokenComando(vencida.id())));

    assertTrue(sesiones.buscarPorId(vencida.id()).orElseThrow().revocadoEn().isEmpty());
  }

  @Test
  void refrescarUnaSesionInexistenteLanzaInvalida() {
    RefrescarToken caso = crear();

    assertThrows(
        SesionDeRefrescoInvalidaException.class,
        () -> caso.ejecutar(new RefrescarTokenComando(UUID.randomUUID())));
  }
}
