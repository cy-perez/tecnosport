package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TopeDeUnidadesPorLineaTest {

  @Test
  void hastaElTopeSePideYUnaMasNo() {
    TopeDeUnidadesPorLinea tope = new TopeDeUnidadesPorLinea(5);

    assertDoesNotThrow(() -> tope.exigir(5));
    CantidadPorLineaExcedidaException error =
        assertThrows(CantidadPorLineaExcedidaException.class, () -> tope.exigir(6));
    assertEquals(5, error.maximo());
  }

  /** El mensaje sale al cliente: dice el tope, que es público, y nada del inventario. */
  @Test
  void elMensajeDiceElTopeYNoLaExistencia() {
    CantidadPorLineaExcedidaException error =
        assertThrows(
            CantidadPorLineaExcedidaException.class,
            () -> new TopeDeUnidadesPorLinea(5).exigir(40));

    assertEquals("Puedes llevar hasta 5 unidades de cada producto.", error.getMessage());
    assertFalse(error.getMessage().contains("40"));
  }

  @Test
  void unTopeQueNoEsPositivoNoSeConstruye() {
    assertThrows(IllegalArgumentException.class, () -> new TopeDeUnidadesPorLinea(0));
  }
}
