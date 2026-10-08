package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.legal.RepositorioAutorizacionesFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** ADR-0074: entrar, o crear la cuenta, con Google. */
class IniciarSesionConGoogleTest {

  private static final Instant AHORA = Instant.parse("2026-10-08T12:00:00Z");
  private static final String SUB = "112233445566778899";
  private static final String CORREO = "ana@gmail.com";

  private final RepositorioUsuariosFalso usuarios = new RepositorioUsuariosFalso();
  private final RepositorioSesionesFalso sesiones = new RepositorioSesionesFalso();
  private final RepositorioAutorizacionesFalso autorizaciones =
      new RepositorioAutorizacionesFalso();
  private final LimitadorDeIntentosFalso limitador = new LimitadorDeIntentosFalso();
  private IdentidadGoogle identidad = new IdentidadGoogle(SUB, CORREO, true);

  /** El doble: la credencial "valida" es la única que pasa, y dice lo que `identidad` diga. */
  private final VerificadorDeCredencialGoogle verificador =
      credencial -> {
        if (!"valida".equals(credencial)) {
          throw new CredencialGoogleInvalidaException();
        }
        return identidad;
      };

  private IniciarSesionConGoogle crear(String clienteId) {
    return new IniciarSesionConGoogle(
        verificador,
        usuarios,
        sesiones,
        new GeneradorDeTokensFalso(),
        autorizaciones,
        new RelojFalso(AHORA),
        Duration.ofDays(30),
        limitador,
        5,
        Duration.ofMinutes(15),
        "2026-10-08.2",
        clienteId);
  }

  private IniciarSesionConGoogle crear() {
    return crear("cliente.apps.googleusercontent.com");
  }

  private static IniciarSesionConGoogleComando comando(boolean autoriza) {
    return new IniciarSesionConGoogleComando("valida", autoriza, "190.24.10.5");
  }

  @Test
  void unaCuentaNuevaNaceVerificadaUnidaAGoogleYConSuConstancia() {
    TokensDeSesion tokens = crear().ejecutar(comando(true));

    Usuario creado = usuarios.buscarPorGoogleSub(SUB).orElseThrow();
    assertEquals(CORREO, creado.correo().valor());
    assertTrue(creado.correoVerificado());
    assertFalse(creado.tieneClave());
    assertEquals(Rol.CLIENTE, tokens.rol());
    assertEquals(1, sesiones.todas().size());
    assertEquals(1, autorizaciones.todas().size());
    assertEquals(OrigenAutorizacion.GOOGLE, autorizaciones.todas().getFirst().origen());
  }

  /** Ley 1581: sin el sí no hay cuenta, ni constancia, ni sesión. */
  @Test
  void sinAutorizarLosDatosNoSeCreaLaCuenta() {
    assertThrows(CuentaGoogleSinRegistroException.class, () -> crear().ejecutar(comando(false)));

    assertTrue(usuarios.buscarPorGoogleSub(SUB).isEmpty());
    assertTrue(autorizaciones.todas().isEmpty());
    assertTrue(sesiones.todas().isEmpty());
  }

  /** Una cuenta que ya existe entra sin volver a autorizar: ya lo hizo al crearse. */
  @Test
  void unaCuentaYaUnidaEntraSinPedirAutorizacion() {
    usuarios.conUsuario(Usuario.crearConGoogle(new CorreoElectronico(CORREO), SUB, AHORA));

    crear().ejecutar(comando(false));

    assertEquals(1, sesiones.todas().size());
    assertTrue(autorizaciones.todas().isEmpty());
  }

  /** La cuenta con clave y verificada se une a Google y conserva su clave. */
  @Test
  void unaCuentaVerificadaDelMismoCorreoSeUneYConservaSuClave() {
    Usuario conClave =
        Usuario.crear(new CorreoElectronico(CORREO), "hash", Rol.CLIENTE, AHORA.minusSeconds(60));
    conClave.verificarCorreo(AHORA.minusSeconds(30));
    usuarios.conUsuario(conClave);

    crear().ejecutar(comando(false));

    Usuario unido = usuarios.buscarPorId(conClave.id()).orElseThrow();
    assertEquals(Optional.of(SUB), unido.googleSub());
    assertTrue(unido.tieneClave());
  }

  /**
   * La toma de cuenta que encontró la revisión de seguridad: alguien registra el correo ajeno con
   * una clave suya, sin poder verificarlo, y espera. Cuando la dueña entra con Google, la cuenta se
   * une y queda verificada — y la clave del que la creó tiene que dejar de servir.
   */
  @Test
  void unaCuentaSinVerificarQueSeUnePierdeLaClaveDeQuienLaCreo() {
    Usuario delAtacante =
        Usuario.crear(new CorreoElectronico(CORREO), "hash", Rol.CLIENTE, AHORA.minusSeconds(60));
    usuarios.conUsuario(delAtacante);

    crear().ejecutar(comando(false));

    Usuario unido = usuarios.buscarPorId(delAtacante.id()).orElseThrow();
    assertEquals(Optional.of(SUB), unido.googleSub());
    assertTrue(unido.correoVerificado());
    assertFalse(unido.tieneClave());
  }

  /**
   * Un correo que no es de Gmail ni de un dominio de Google: Google no manda sobre ese buzón y no
   * se une por el correo. Crear una cuenta nueva con él sí, porque eso no le entrega nada a nadie.
   */
  @Test
  void sinAutoridadDeGoogleSobreElBuzonNoSeUnePorCorreo() {
    identidad = new IdentidadGoogle(SUB, "ana@empresa.co", true, false);
    Usuario existente =
        Usuario.crear(new CorreoElectronico("ana@empresa.co"), "hash", Rol.CLIENTE, AHORA);
    existente.verificarCorreo(AHORA);
    usuarios.conUsuario(existente);

    assertThrows(
        CuentaExistenteRequiereClaveException.class, () -> crear().ejecutar(comando(true)));
    assertTrue(usuarios.buscarPorId(existente.id()).orElseThrow().googleSub().isEmpty());
  }

  @Test
  void sinAutoridadDeGoogleSiSeCreaUnaCuentaNueva() {
    identidad = new IdentidadGoogle(SUB, "ana@empresa.co", true, false);

    crear().ejecutar(comando(true));

    assertTrue(usuarios.buscarPorGoogleSub(SUB).isPresent());
  }

  /**
   * Sin correo verificado no se puede afirmar de quién es, y unirlo entregaría una cuenta ajena.
   */
  @Test
  void unCorreoQueGoogleNoVerificoNoEntra() {
    identidad = new IdentidadGoogle(SUB, CORREO, false);
    usuarios.conUsuario(Usuario.crear(new CorreoElectronico(CORREO), "hash", Rol.CLIENTE, AHORA));

    assertThrows(CredencialGoogleInvalidaException.class, () -> crear().ejecutar(comando(true)));
    assertTrue(sesiones.todas().isEmpty());
  }

  @Test
  void unaCredencialQueNoPasaLaVerificacionNoEntra() {
    assertThrows(
        CredencialGoogleInvalidaException.class,
        () ->
            crear()
                .ejecutar(new IniciarSesionConGoogleComando("falsificada", true, "190.24.10.5")));
  }

  /** El panel tiene su propia puerta: una cuenta ADMIN no entra con Google. */
  @Test
  void elAdministradorNoEntraConGoogle() {
    Usuario admin = Usuario.crear(new CorreoElectronico(CORREO), "hash", Rol.ADMIN, AHORA);
    admin.verificarCorreo(AHORA);
    usuarios.conUsuario(admin);

    assertThrows(CredencialesInvalidasException.class, () -> crear().ejecutar(comando(true)));
    assertTrue(sesiones.todas().isEmpty());
  }

  @Test
  void sinClienteConfiguradoLaPuertaNoExiste() {
    IniciarSesionConGoogle caso = crear("");

    assertFalse(caso.habilitado());
    assertThrows(GoogleNoHabilitadoException.class, () -> caso.ejecutar(comando(true)));
  }
}
