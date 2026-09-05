package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UsuarioTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("admin@tecnosport.co");

  @Test
  void crearConDatosValidosFunciona() {
    Usuario usuario = Usuario.crear(CORREO, "hash-argon2id", Rol.ADMIN, AHORA);

    assertEquals(CORREO, usuario.correo());
    assertEquals(Rol.ADMIN, usuario.rol());
    assertEquals("hash-argon2id", usuario.claveHash());
    assertEquals(AHORA, usuario.creadoEn());
  }

  @Test
  void claveHashVaciaSeRechaza() {
    assertThrows(ExcepcionDeDominio.class, () -> Usuario.crear(CORREO, "  ", Rol.ADMIN, AHORA));
  }

  @Test
  void claveHashNulaSeRechaza() {
    assertThrows(ExcepcionDeDominio.class, () -> Usuario.crear(CORREO, null, Rol.ADMIN, AHORA));
  }

  @Test
  void naceSinVerificar() {
    Usuario usuario = Usuario.crear(CORREO, "hash-argon2id", Rol.CLIENTE, AHORA);

    assertFalse(usuario.correoVerificado());
    assertTrue(usuario.correoVerificadoEn().isEmpty());
  }

  @Test
  void verificarCorreoLoMarcaVerificado() {
    Usuario usuario = Usuario.crear(CORREO, "hash-argon2id", Rol.CLIENTE, AHORA);

    usuario.verificarCorreo(AHORA);

    assertTrue(usuario.correoVerificado());
    assertEquals(AHORA, usuario.correoVerificadoEn().orElseThrow());
  }

  @Test
  void verificarCorreoEsIdempotente() {
    Usuario usuario = Usuario.crear(CORREO, "hash-argon2id", Rol.CLIENTE, AHORA);

    usuario.verificarCorreo(AHORA);
    usuario.verificarCorreo(AHORA.plusSeconds(60));

    assertEquals(AHORA, usuario.correoVerificadoEn().orElseThrow());
  }

  @Test
  void cambiarClaveReemplazaElHash() {
    Usuario usuario = Usuario.crear(CORREO, "hash-viejo", Rol.CLIENTE, AHORA);

    usuario.cambiarClave("hash-nuevo");

    assertEquals("hash-nuevo", usuario.claveHash());
  }

  @Test
  void cambiarClaveVaciaSeRechaza() {
    Usuario usuario = Usuario.crear(CORREO, "hash-viejo", Rol.CLIENTE, AHORA);

    assertThrows(ExcepcionDeDominio.class, () -> usuario.cambiarClave("  "));
  }

  @Test
  void cambiarClaveNulaSeRechaza() {
    Usuario usuario = Usuario.crear(CORREO, "hash-viejo", Rol.CLIENTE, AHORA);

    assertThrows(ExcepcionDeDominio.class, () -> usuario.cambiarClave(null));
  }
}
