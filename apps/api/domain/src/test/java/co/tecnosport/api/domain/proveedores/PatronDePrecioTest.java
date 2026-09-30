package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PatronDePrecioTest {

  /** Los 25 mensajes del anexo, en orden, sacados del documento sin transcribir. */
  private static List<String> anexo() {
    try (InputStream entrada =
        PatronDePrecioTest.class.getResourceAsStream("/proveedores/anexo-mensajes.txt")) {
      String todo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
      return Arrays.stream(todo.split("\n--- Fotos ---\n")).map(String::strip).toList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final long[] PRECIOS_DEL_ANEXO = {
    53000, 60000, 62000, 52000, 60000, 60000, 53000, 40000, 43000, 43000, 65000, 60000, 58000,
    55000, 60000, 45000, 45000, 45000, 45000, 40000, 60000, 30000, 45000, 50000, 50000
  };

  /**
   * El primer criterio de aceptación empieza aquí: si esto falla, los borradores salen con otro
   * precio.
   */
  @Test
  void leeElPrecioDeLosVeinticincoMensajesDelAnexo() {
    List<String> mensajes = anexo();
    assertEquals(25, mensajes.size());

    for (int i = 0; i < mensajes.size(); i++) {
      Optional<Dinero> precio = PatronDePrecio.extraer(mensajes.get(i));
      assertEquals(
          Optional.of(Dinero.deCop(PRECIOS_DEL_ANEXO[i])),
          precio,
          "mensaje " + (i + 1) + ":\n" + mensajes.get(i));
    }
  }

  @Test
  void lasCuatroFormasVistasEnLosChats() {
    assertEquals(Optional.of(Dinero.deCop(53000)), PatronDePrecio.extraer("💰 *53.000*"));
    assertEquals(Optional.of(Dinero.deCop(60000)), PatronDePrecio.extraer("💰*60.000*"));
    assertEquals(Optional.of(Dinero.deCop(60000)), PatronDePrecio.extraer("*PRECIO: $60.000💰*"));
    assertEquals(Optional.of(Dinero.deCop(62000)), PatronDePrecio.extraer("💰62.000_"));
    assertEquals(Optional.of(Dinero.deCop(62000)), PatronDePrecio.extraer("Vale 62.000_"));
    assertEquals(Optional.of(Dinero.deCop(45000)), PatronDePrecio.extraer("PRECIO $45000"));
    assertEquals(Optional.of(Dinero.deCop(45000)), PatronDePrecio.extraer("45,000💰"));
  }

  /** Un teléfono, una fecha o un «3 compartimientos» no son precios: no llevan marca. */
  @Test
  void unNumeroSinMarcaNoEsUnPrecio() {
    assertFalse(PatronDePrecio.tienePrecio("*📲 +57 321 9427252*"));
    assertFalse(PatronDePrecio.tienePrecio("3 Compartimientos internos 🪄"));
    assertFalse(PatronDePrecio.tienePrecio("C.C FICUS Lc 1C-14 & 1C-13"));
    assertFalse(PatronDePrecio.tienePrecio("Tallas M-L-XL-XXL"));
    assertFalse(PatronDePrecio.tienePrecio("*NUEVA POLO 1.1🍯*"));
    assertFalse(PatronDePrecio.tienePrecio("https://wa.link/ppx8li"));
    assertFalse(PatronDePrecio.tienePrecio(null));
    assertFalse(PatronDePrecio.tienePrecio("   "));
  }

  /** Con dos precios gana el primero: el «por difusión» va delante del «después de 6». */
  @Test
  void conDosPreciosGanaElPrimero() {
    String texto = "*PRECIO X DIFUSIÓN $50.000💰*\n*DESPUÉS DE 6 $45.000📦*";

    assertEquals(Optional.of(Dinero.deCop(50000)), PatronDePrecio.extraer(texto));
  }

  @Test
  void unaCifraDeMenosDeMilConMarcaNoCuenta() {
    assertTrue(PatronDePrecio.extraer("💰 500").isEmpty());
  }
}
