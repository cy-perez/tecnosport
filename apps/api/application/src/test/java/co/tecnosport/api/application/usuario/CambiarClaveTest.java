package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CambiarClaveTest {

  private static final Instant AHORA = Instant.parse("2026-09-22T12:00:00Z");
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);
  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(15);
  private static final CorreoElectronico CORREO = new CorreoElectronico("admin@tecnosport.co");

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSesionesFalso sesiones;
  private CodificadorDeClavesFalso codificador;
  private LimitadorDeIntentosFalso limitadorDeIntentos;

  private CambiarClave crear() {
    usuarios = new RepositorioUsuariosFalso();
    sesiones = new RepositorioSesionesFalso();
    codificador = new CodificadorDeClavesFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    return new CambiarClave(
        usuarios,
        sesiones,
        codificador,
        new GeneradorDeTokensFalso(),
        new RelojFalso(AHORA),
        VIGENCIA_REFRESCO,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA);
  }

  private Usuario conAdmin(String claveTextoPlano) {
    Usuario usuario =
        Usuario.crear(CORREO, codificador.codificar(claveTextoPlano), Rol.ADMIN, AHORA);
    usuario.verificarCorreo(AHORA);
    usuarios.conUsuario(usuario);
    return usuario;
  }

  /** La sesión con la que llega, abierta en otro momento: la que tiene que quedar revocada. */
  private SesionRefresco conSesionAbiertaDe(Usuario usuario) {
    SesionRefresco sesion =
        SesionRefresco.crear(
            usuario.id(),
            GeneradorIdentificador.nuevo(),
            AHORA.minus(Duration.ofDays(1)),
            VIGENCIA_REFRESCO);
    sesiones.guardar(sesion);
    return sesion;
  }

  @Test
  void laClaveCorrectaCambiaElHash() {
    CambiarClave caso = crear();
    Usuario usuario = conAdmin("clave-vieja");

    caso.ejecutar(new CambiarClaveComando(usuario.id(), "clave-vieja", "clave-nueva"));

    assertEquals(
        codificador.codificar("clave-nueva"),
        usuarios.buscarPorId(usuario.id()).orElseThrow().claveHash());
  }

  @Test
  void quienCambiaSuClaveSigueDentroYLoDemasQuedaFuera() {
    CambiarClave caso = crear();
    Usuario usuario = conAdmin("clave-vieja");
    SesionRefresco anterior = conSesionAbiertaDe(usuario);

    TokensDeSesion tokens =
        caso.ejecutar(new CambiarClaveComando(usuario.id(), "clave-vieja", "clave-nueva"));

    assertTrue(
        sesiones.buscarPorId(anterior.id()).orElseThrow().revocadoEn().isPresent(),
        "la sesión que ya estaba abierta tiene que quedar revocada");
    assertTrue(
        sesiones.buscarPorId(tokens.refreshTokenId()).orElseThrow().estaVigente(AHORA),
        "la sesión que se devuelve tiene que estar vigente: revocar todas no puede alcanzarla");
    assertEquals(Rol.ADMIN, tokens.rol());
    assertEquals(usuario.id(), tokens.usuarioId());
  }

  @Test
  void laClaveActualIncorrectaNoCambiaNadaNiCierraSesiones() {
    CambiarClave caso = crear();
    Usuario usuario = conAdmin("clave-vieja");
    SesionRefresco anterior = conSesionAbiertaDe(usuario);

    assertThrows(
        CredencialesInvalidasException.class,
        () ->
            caso.ejecutar(new CambiarClaveComando(usuario.id(), "no-es-mi-clave", "clave-nueva")));

    assertEquals(
        codificador.codificar("clave-vieja"),
        usuarios.buscarPorId(usuario.id()).orElseThrow().claveHash());
    assertTrue(sesiones.buscarPorId(anterior.id()).orElseThrow().estaVigente(AHORA));
  }

  @Test
  void unUsuarioQueNoExisteLanzaCredencialesInvalidas() {
    CambiarClave caso = crear();

    assertThrows(
        CredencialesInvalidasException.class,
        () -> caso.ejecutar(new CambiarClaveComando(UUID.randomUUID(), "cualquiera", "otra")));
  }

  @Test
  void elLimiteExcedidoCortaAntesDeVerificarLaClave() {
    CambiarClave caso = crear();
    Usuario usuario = conAdmin("clave-vieja");
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class,
        () -> caso.ejecutar(new CambiarClaveComando(usuario.id(), "clave-vieja", "clave-nueva")));

    assertEquals(
        codificador.codificar("clave-vieja"),
        usuarios.buscarPorId(usuario.id()).orElseThrow().claveHash());
    assertFalse(
        sesiones.todas().stream().anyMatch(s -> s.usuarioId().equals(usuario.id())),
        "un intento cortado por el límite no abre ninguna sesión");
  }
}
