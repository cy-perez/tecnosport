package co.tecnosport.api.domain.sugerencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SugerenciaTest {

  private static final Instant AHORA = Instant.parse("2026-09-26T15:00:00Z");

  @Test
  void unaSugerenciaSinCorreoEsValida() {
    Sugerencia sugerencia =
        Sugerencia.recibir("El buscador podría filtrar por talla.", null, AHORA);

    assertEquals(Optional.empty(), sugerencia.correo());
    assertEquals("El buscador podría filtrar por talla.", sugerencia.mensaje());
    assertEquals(AHORA, sugerencia.recibidaEn());
  }

  @Test
  void conCorreoLoGuarda() {
    Sugerencia sugerencia =
        Sugerencia.recibir(
            "Gracias por el envío.", new CorreoElectronico("ana@ejemplo.com"), AHORA);

    assertEquals(Optional.of(new CorreoElectronico("ana@ejemplo.com")), sugerencia.correo());
  }

  /**
   * Un formulario que solo valida en el navegador es un formulario sin validar: la ruta es pública
   * y cualquiera puede mandarle un cuerpo a mano.
   */
  @Test
  void unMensajeEnBlancoNoEsUnaSugerencia() {
    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> Sugerencia.recibir("   \n  ", null, AHORA));

    assertTrue(error.getMessage().contains("sin mensaje"), error.getMessage());
  }

  @Test
  void unMensajeNuloTampoco() {
    assertThrows(ExcepcionDeDominio.class, () -> Sugerencia.recibir(null, null, AHORA));
  }

  /**
   * El tope es de dominio y no de la columna, que es {@code text}. Aquí es donde tiene que fallar,
   * con un mensaje que quien escribe pueda entender, y no como un 500 crudo de Postgres.
   */
  @Test
  void unMensajeMasLargoQueElTopeSeRechaza() {
    String largo = "a".repeat(Sugerencia.MAXIMO_CARACTERES_MENSAJE + 1);

    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> Sugerencia.recibir(largo, null, AHORA));

    assertTrue(
        error.getMessage().contains(String.valueOf(Sugerencia.MAXIMO_CARACTERES_MENSAJE)),
        error.getMessage());
  }

  /**
   * Justo en el tope entra. Un fuera-de-rango mal puesto se descubre en el borde y no en el centro,
   * y aquí el borde lo mira alguien que acaba de escribir dos mil caracteres.
   */
  @Test
  void unMensajeExactamenteEnElTopeEntra() {
    String justo = "a".repeat(Sugerencia.MAXIMO_CARACTERES_MENSAJE);

    assertEquals(justo.length(), Sugerencia.recibir(justo, null, AHORA).mensaje().length());
  }

  /**
   * Se recorta antes de medir: dos mil caracteres y un salto de línea al final no son dos mil uno.
   * Sin esto, pegar un texto desde otra aplicación —que casi siempre arrastra un espacio—
   * rechazaría un mensaje que cabe.
   */
  @Test
  void losEspaciosDeLosExtremosNoCuentanParaElTope() {
    String justo = "  " + "a".repeat(Sugerencia.MAXIMO_CARACTERES_MENSAJE) + "\n ";

    assertEquals(
        Sugerencia.MAXIMO_CARACTERES_MENSAJE,
        Sugerencia.recibir(justo, null, AHORA).mensaje().length());
  }
}
