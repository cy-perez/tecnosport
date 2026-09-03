package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
