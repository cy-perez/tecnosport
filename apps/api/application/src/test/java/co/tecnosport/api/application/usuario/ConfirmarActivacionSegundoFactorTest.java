package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.ActivacionSegundoFactorNoIniciadaException;
import co.tecnosport.api.domain.usuario.CodigoSegundoFactorInvalidoException;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmarActivacionSegundoFactorTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");
  private static final Duration PENDIENTE = Duration.ofMinutes(15);

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSegundosFactoresFalso factores;
  private RepositorioSesionesFalso sesiones;
  private EnviadorDeCorreoFalso correos;
  private LimitadorDeIntentosFalso limitador;
  private Usuario admin;

  private ConfirmarActivacionSegundoFactor caso() {
    return new ConfirmarActivacionSegundoFactor(
        usuarios,
        factores,
        sesiones,
        new GeneradorDeTokensFalso(),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA),
        Duration.ofDays(30),
        PENDIENTE,
        limitador,
        5,
        Duration.ofMinutes(15));
  }

  @BeforeEach
  void preparar() {
    usuarios = new RepositorioUsuariosFalso();
    factores = new RepositorioSegundosFactoresFalso();
    sesiones = new RepositorioSesionesFalso();
    correos = new EnviadorDeCorreoFalso();
    limitador = new LimitadorDeIntentosFalso();
    admin =
        Usuario.crear(new CorreoElectronico("admin@tecnosport.co"), "hash:clave", Rol.ADMIN, AHORA);
    admin.verificarCorreo(AHORA);
    usuarios.conUsuario(admin);
  }

  private SegundoFactor pendiente() {
    SegundoFactor factor = SegundoFactor.iniciar(admin.id(), AHORA);
    factores.con(factor);
    return factor;
  }

  private static String codigo(SegundoFactor factor) {
    return Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA));
  }

  @Test
  void activaElFactorDevuelveLosCodigosRevocaLasSesionesYAvisa() {
    SegundoFactor factor = pendiente();
    SesionRefresco vieja =
        SesionRefresco.crear(admin.id(), UUID.randomUUID(), AHORA, Duration.ofDays(30));
    sesiones.guardar(vieja);

    ActivacionConfirmada confirmada =
        caso().ejecutar(new ConfirmarActivacionSegundoFactorComando(admin.id(), codigo(factor)));

    SegundoFactor guardado = factores.de(admin.id()).orElseThrow();
    assertTrue(guardado.activo());
    assertEquals(10, confirmada.codigosDeRespaldo().size());
    assertEquals(10, guardado.restantes());
    assertTrue(sesiones.buscarPorId(vieja.id()).orElseThrow().revocadoEn().isPresent());
    SesionRefresco nueva = sesiones.buscarPorId(confirmada.sesion().refreshTokenId()).orElseThrow();
    assertTrue(nueva.estaVigente(AHORA));
    assertEquals(1, correos.enviados().size());
    assertEquals("[usuario.segundo_factor.activado.asunto]", correos.enviados().get(0).asunto());
    assertEquals(List.of("cuenta:confirmar-segundo-factor:" + admin.id()), limitador.olvidadas());
  }

  @Test
  void conUnCodigoEquivocadoNoActivaNiRevocaNiAvisa() {
    SegundoFactor factor = pendiente();
    SesionRefresco vieja =
        SesionRefresco.crear(admin.id(), UUID.randomUUID(), AHORA, Duration.ofDays(30));
    sesiones.guardar(vieja);
    String malo = Totp.codigoEn(factor.clave(), Totp.pasoDe(AHORA) + 5);

    assertThrows(
        CodigoSegundoFactorInvalidoException.class,
        () -> caso().ejecutar(new ConfirmarActivacionSegundoFactorComando(admin.id(), malo)));

    assertFalse(factores.de(admin.id()).orElseThrow().activo());
    assertTrue(sesiones.buscarPorId(vieja.id()).orElseThrow().estaVigente(AHORA));
    assertTrue(correos.enviados().isEmpty());
    assertTrue(limitador.olvidadas().isEmpty());
  }

  @Test
  void sinActivacionIniciadaResponde409() {
    assertThrows(
        ActivacionSegundoFactorNoIniciadaException.class,
        () -> caso().ejecutar(new ConfirmarActivacionSegundoFactorComando(admin.id(), "123456")));
  }

  @Test
  void conElLimiteAgotadoNoMiraElCodigo() {
    SegundoFactor factor = pendiente();
    limitador.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () ->
            caso()
                .ejecutar(new ConfirmarActivacionSegundoFactorComando(admin.id(), codigo(factor))));
    assertFalse(factores.de(admin.id()).orElseThrow().activo());
  }
}
