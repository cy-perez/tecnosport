package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base32Test {

  private static byte[] ascii(String texto) {
    return texto.getBytes(StandardCharsets.US_ASCII);
  }

  /** RFC 4648, sección 10, sin el relleno. */
  @Test
  void codificaLosVectoresDeLaRfc4648SinRelleno() {
    assertEquals("", Base32.codificar(ascii("")));
    assertEquals("MY", Base32.codificar(ascii("f")));
    assertEquals("MZXQ", Base32.codificar(ascii("fo")));
    assertEquals("MZXW6", Base32.codificar(ascii("foo")));
    assertEquals("MZXW6YQ", Base32.codificar(ascii("foob")));
    assertEquals("MZXW6YTB", Base32.codificar(ascii("fooba")));
    assertEquals("MZXW6YTBOI", Base32.codificar(ascii("foobar")));
  }

  @Test
  void decodificaLoQueUnaPersonaTecleaOPega() {
    assertArrayEquals(ascii("foobar"), Base32.decodificar("MZXW6YTBOI"));
    assertArrayEquals(ascii("foobar"), Base32.decodificar("mzxw6ytboi"));
    assertArrayEquals(ascii("foobar"), Base32.decodificar("MZXW 6YTB OI"));
    assertArrayEquals(ascii("foobar"), Base32.decodificar("MZXW-6YTB-OI======"));
    assertArrayEquals(ascii("f"), Base32.decodificar("MY"));
  }

  @Test
  void idaYVuelta() {
    byte[] datos = ClaveTotp.generar().bytes();
    assertArrayEquals(datos, Base32.decodificar(Base32.codificar(datos)));
  }

  @Test
  void rechazaLoQueNoEsDelAlfabeto() {
    // El 0, el 1 y el 8 no están: se confunden con la O, la I y la B.
    assertThrows(ExcepcionDeDominio.class, () -> Base32.decodificar("MZXW1"));
    assertThrows(ExcepcionDeDominio.class, () -> Base32.decodificar("MZXW8"));
  }
}
