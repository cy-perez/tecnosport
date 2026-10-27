package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.SegundoFactorYaActivoException;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IniciarActivacionSegundoFactorTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSegundosFactoresFalso factores;
  private LimitadorDeIntentosFalso limitador;
  private Usuario admin;

  private IniciarActivacionSegundoFactor caso() {
    return new IniciarActivacionSegundoFactor(
        usuarios,
        factores,
        new CodificadorDeClavesFalso(),
        new RelojFalso(AHORA),
        limitador,
        5,
        Duration.ofMinutes(15),
        "Tecno Sport");
  }

  @BeforeEach
  void preparar() {
    usuarios = new RepositorioUsuariosFalso();
    factores = new RepositorioSegundosFactoresFalso();
    limitador = new LimitadorDeIntentosFalso();
    admin =
        Usuario.crear(new CorreoElectronico("admin@tecnosport.co"), "hash:clave", Rol.ADMIN, AHORA);
    admin.verificarCorreo(AHORA);
    usuarios.conUsuario(admin);
  }

  @Test
  void dejaUnFactorPendienteYDevuelveLaClaveYLaUri() {
    ActivacionIniciada activacion =
        caso().ejecutar(new IniciarActivacionSegundoFactorComando(admin.id(), "clave"));

    SegundoFactor guardado = factores.de(admin.id()).orElseThrow();
    assertFalse(guardado.activo());
    assertEquals(guardado.clave().enBase32(), activacion.claveBase32());
    assertEquals(6, activacion.digitos());
    assertEquals(30, activacion.periodoSegundos());
    assertEquals(
        "otpauth://totp/Tecno%20Sport:admin%40tecnosport.co?secret="
            + activacion.claveBase32()
            + "&issuer=Tecno%20Sport&algorithm=SHA1&digits=6&period=30",
        activacion.uri());
    assertEquals(List.of("cuenta:activar-segundo-factor:" + admin.id()), limitador.olvidadas());
  }

  @Test
  void conLaClaveEquivocadaNoGuardaNada() {
    assertThrows(
        CredencialesInvalidasException.class,
        () -> caso().ejecutar(new IniciarActivacionSegundoFactorComando(admin.id(), "otra")));
    assertTrue(factores.de(admin.id()).isEmpty());
    assertTrue(limitador.olvidadas().isEmpty());
  }

  @Test
  void conElLimiteAgotadoNiSiquieraMiraLaClave() {
    limitador.denegarSiempre();
    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso().ejecutar(new IniciarActivacionSegundoFactorComando(admin.id(), "clave")));
  }

  @Test
  void unaActivacionPendienteSeReemplazaConOtraClave() {
    String primera =
        caso()
            .ejecutar(new IniciarActivacionSegundoFactorComando(admin.id(), "clave"))
            .claveBase32();
    String segunda =
        caso()
            .ejecutar(new IniciarActivacionSegundoFactorComando(admin.id(), "clave"))
            .claveBase32();

    assertNotEquals(primera, segunda);
    assertEquals(segunda, factores.de(admin.id()).orElseThrow().clave().enBase32());
  }

  @Test
  void unFactorActivoNoSeReemplaza() {
    SegundoFactor activo = SegundoFactor.iniciar(admin.id(), AHORA);
    activo.confirmar(
        Totp.codigoEn(activo.clave(), Totp.pasoDe(AHORA)), AHORA, Duration.ofMinutes(15));
    factores.con(activo);

    assertThrows(
        SegundoFactorYaActivoException.class,
        () -> caso().ejecutar(new IniciarActivacionSegundoFactorComando(admin.id(), "clave")));
    assertEquals(activo.clave(), factores.de(admin.id()).orElseThrow().clave());
  }
}
