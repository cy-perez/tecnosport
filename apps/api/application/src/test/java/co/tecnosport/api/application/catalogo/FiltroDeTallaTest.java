package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import org.junit.jupiter.api.Test;

class FiltroDeTallaTest {

  /** Normalizada como la devuelve el listado: si no, «2xl» no encontraría las XXL. */
  @Test
  void normalizaLaTallaComoLaComparaLaVitrina() {
    assertEquals("XXL", new FiltroDeTalla(LineaCatalogo.ROPA, " 2xl ").talla());
    assertEquals("UNICA", new FiltroDeTalla(LineaCatalogo.ROPA, "Única").talla());
    assertEquals("38", new FiltroDeTalla(LineaCatalogo.CALZADO, "38").talla());
  }

  @Test
  void soloLaRopaYElCalzadoTienenTalla() {
    assertThrows(
        IllegalArgumentException.class, () -> new FiltroDeTalla(LineaCatalogo.TECNOLOGIA, "M"));
    assertThrows(
        IllegalArgumentException.class, () -> new FiltroDeTalla(LineaCatalogo.BOLSOS, "M"));
  }

  @Test
  void rechazaUnaTallaVacia() {
    assertThrows(IllegalArgumentException.class, () -> new FiltroDeTalla(LineaCatalogo.ROPA, " "));
    assertThrows(IllegalArgumentException.class, () -> new FiltroDeTalla(LineaCatalogo.ROPA, null));
  }
}
