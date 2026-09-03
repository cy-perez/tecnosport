package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IniciarSesionTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA_REFRESCO = Duration.ofDays(30);
  private static final CorreoElectronico CORREO = new CorreoElectronico("admin@tecnosport.co");

  private RepositorioUsuariosFalso usuarios;
  private RepositorioSesionesFalso sesiones;
  private CodificadorDeClavesFalso codificador;

  private IniciarSesion crear() {
    usuarios = new RepositorioUsuariosFalso();
    sesiones = new RepositorioSesionesFalso();
    codificador = new CodificadorDeClavesFalso();
    return new IniciarSesion(
        usuarios,
        sesiones,
        codificador,
        new GeneradorDeTokensFalso(),
        new RelojFalso(AHORA),
        VIGENCIA_REFRESCO);
  }

  private void conUsuarioAdmin(String claveTextoPlano) {
    Usuario usuario =
        Usuario.crear(CORREO, codificador.codificar(claveTextoPlano), Rol.ADMIN, AHORA);
    usuarios.conUsuario(usuario);
  }

  @Test
  void loginExitosoDevuelveTokensYCreaUnaSesion() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");

    TokensDeSesion tokens =
        caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-correcta"));

    assertEquals(Rol.ADMIN, tokens.rol());
    assertEquals(1, sesiones.todas().size());
    assertEquals(tokens.refreshTokenId(), sesiones.todas().get(0).id());
  }

  @Test
  void correoInexistenteLanzaCredencialesInvalidas() {
    IniciarSesion caso = crear();

    assertThrows(
        CredencialesInvalidasException.class,
        () -> caso.ejecutar(new IniciarSesionComando("no-existe@tecnosport.co", "cualquiera")));
  }

  @Test
  void claveIncorrectaLanzaCredencialesInvalidas() {
    IniciarSesion caso = crear();
    conUsuarioAdmin("clave-correcta");

    assertThrows(
        CredencialesInvalidasException.class,
        () -> caso.ejecutar(new IniciarSesionComando("admin@tecnosport.co", "clave-incorrecta")));
  }
}
