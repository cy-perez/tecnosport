package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import org.junit.jupiter.api.Test;

class MarcaTest {

  @Test
  void rechazaNombreVacio() {
    assertThrows(ExcepcionDeDominio.class, () -> Marca.crear("  "));
  }

  /**
   * Mientras las marcas entraban por migración esto no podía pasar. Con un formulario detrás, un
   * nombre más largo que la columna llegaba hasta Hibernate y salía como {@code 500}.
   */
  @Test
  void rechazaNombreMasLargoQueLaColumna() {
    String demasiado = "X".repeat(Marca.LARGO_MAXIMO_NOMBRE + 1);

    assertThrows(ExcepcionDeDominio.class, () -> Marca.crear(demasiado));
  }

  @Test
  void aceptaElNombreDelLargoExacto() {
    String justo = "X".repeat(Marca.LARGO_MAXIMO_NOMBRE);

    assertEquals(justo, Marca.crear(justo).nombre());
  }

  /**
   * El recorte no es cosmético: es lo que hace que " Xiaomi " y "Xiaomi" sean la misma marca para
   * la comprobación de duplicados de {@code CrearMarca}.
   */
  @Test
  void recortaLosEspaciosDeLosExtremos() {
    assertEquals("Xiaomi", Marca.crear("  Xiaomi  ").nombre());
  }

  /** El largo se mide sobre el nombre ya recortado, no sobre lo que llegó. */
  @Test
  void losEspaciosDeLosExtremosNoCuentanParaElLargo() {
    String justo = "X".repeat(Marca.LARGO_MAXIMO_NOMBRE);

    assertEquals(justo, Marca.crear("   " + justo + "   ").nombre());
  }
}
