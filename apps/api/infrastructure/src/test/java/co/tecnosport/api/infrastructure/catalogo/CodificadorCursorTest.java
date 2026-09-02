package co.tecnosport.api.infrastructure.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CodificadorCursorTest {

  @Test
  void codificarYDecodificarEsReversible() {
    UUID id = UUID.randomUUID();

    String cursor = CodificadorCursor.codificar("89900", id);
    CodificadorCursor.Decodificado decodificado = CodificadorCursor.decodificar(cursor);

    assertEquals("89900", decodificado.claveOrden());
    assertEquals(id, decodificado.id());
  }

  @Test
  void rechazaUnCursorQueNoEsBase64Valido() {
    assertThrows(
        CodificadorCursor.CursorInvalidoException.class,
        () -> CodificadorCursor.decodificar("no-es-base64!!"));
  }

  @Test
  void rechazaUnCursorSinSeparador() {
    String cursorSinSeparador =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("sinseparador".getBytes());

    assertThrows(
        CodificadorCursor.CursorInvalidoException.class,
        () -> CodificadorCursor.decodificar(cursorSinSeparador));
  }

  @Test
  void rechazaUnCursorConIdQueNoEsUuid() {
    String cursorConIdInvalido =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("89900|no-es-un-uuid".getBytes());

    assertThrows(
        CodificadorCursor.CursorInvalidoException.class,
        () -> CodificadorCursor.decodificar(cursorConIdInvalido));
  }
}
