package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Solo la matemática: la reducción de una foto real a 32×32 la prueba la infraestructura, que es
 * quien decodifica.
 */
class PHashTest {

  /** Una "foto" sintética con estructura: un degradado con un bloque claro. */
  private static int[][] bolso() {
    int[][] m = new int[PHash.LADO][PHash.LADO];
    for (int x = 0; x < PHash.LADO; x++) {
      for (int y = 0; y < PHash.LADO; y++) {
        m[x][y] = (x * 4 + y * 3) % 256;
        if (x > 8 && x < 24 && y > 10 && y < 20) {
          m[x][y] = 240;
        }
      }
    }
    return m;
  }

  @Test
  void laMismaLuminanciaDaDistanciaCero() {
    assertEquals(
        0, PHash.desdeLuminancia(bolso()).distanciaHamming(PHash.desdeLuminancia(bolso())));
  }

  /** Recomprimir o aclarar un poco la foto no debe cambiar la huella más que unos bits. */
  @Test
  void unPocoDeRuidoYDeBrilloCambianPocosBits() {
    int[][] original = bolso();
    int[][] tocada = bolso();
    Random ruido = new Random(7);
    for (int x = 0; x < PHash.LADO; x++) {
      for (int y = 0; y < PHash.LADO; y++) {
        tocada[x][y] = Math.min(255, Math.max(0, tocada[x][y] + 12 + ruido.nextInt(7) - 3));
      }
    }

    int distancia = PHash.desdeLuminancia(original).distanciaHamming(PHash.desdeLuminancia(tocada));

    assertTrue(distancia <= 6, "distancia " + distancia);
  }

  @Test
  void dosImagenesDistintasQuedanLejos() {
    int[][] otra = new int[PHash.LADO][PHash.LADO];
    Random azar = new Random(42);
    for (int x = 0; x < PHash.LADO; x++) {
      for (int y = 0; y < PHash.LADO; y++) {
        otra[x][y] = azar.nextInt(256);
      }
    }

    int distancia = PHash.desdeLuminancia(bolso()).distanciaHamming(PHash.desdeLuminancia(otra));

    assertTrue(distancia > 16, "distancia " + distancia);
  }

  @Test
  void elHexVaYVuelve() {
    PHash huella = PHash.desdeLuminancia(bolso());

    assertEquals(16, huella.hex().length());
    assertEquals(huella, PHash.deHex(huella.hex()));
    assertThrows(ExcepcionDeDominio.class, () -> PHash.deHex("xyz"));
  }

  @Test
  void laMatrizTieneQueSerDeTreintaYDos() {
    assertThrows(ExcepcionDeDominio.class, () -> PHash.desdeLuminancia(new int[8][8]));
    assertThrows(ExcepcionDeDominio.class, () -> PHash.desdeLuminancia(new int[32][16]));
  }
}
