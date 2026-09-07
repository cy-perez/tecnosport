package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HashContenidoTest {

  private static final String SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @Test
  void normalizaAMinusculasYRecortaEspacios() {
    HashContenido hash = new HashContenido("  " + SHA256.toUpperCase() + "  ");

    assertEquals(SHA256, hash.valor());
  }

  @Test
  void rechazaVacio() {
    assertThrows(HashContenidoInvalidoException.class, () -> new HashContenido("   "));
  }

  @Test
  void rechazaNulo() {
    assertThrows(HashContenidoInvalidoException.class, () -> new HashContenido(null));
  }

  @Test
  void rechazaMenosDe64Caracteres() {
    assertThrows(
        HashContenidoInvalidoException.class, () -> new HashContenido(SHA256.substring(1)));
  }

  @Test
  void rechazaCaracteresQueNoSonHexadecimales() {
    assertThrows(
        HashContenidoInvalidoException.class, () -> new HashContenido("z" + SHA256.substring(1)));
  }

  /**
   * El defecto concreto que este objeto de valor existe para impedir: el código guardaba la key del
   * objeto de Cloud Storage donde va el hash, y ni siquiera cabía en la columna.
   */
  @Test
  void rechazaUnaKeyDeCloudStorage() {
    assertThrows(
        HashContenidoInvalidoException.class,
        () ->
            new HashContenido(
                "productos/01a0782a-82a8-7c84-81a8-8736fd79bd43"
                    + "/rotacion/01a079ed-aa02-7ecd-9927-d7b330b44fe4/0.webp"));
  }
}
