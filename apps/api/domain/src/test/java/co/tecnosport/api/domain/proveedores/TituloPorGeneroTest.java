package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TituloPorGeneroTest {

  @Test
  void unisexSeVuelveElGeneroDeCadaParte() {
    assertEquals(
        "Tenis estilo Nike para dama", TituloPorGenero.para("Tenis estilo Nike unisex", "dama"));
    assertEquals(
        "Tenis estilo Nike para caballero",
        TituloPorGenero.para("Tenis estilo Nike unisex", "caballero"));
  }

  @Test
  void otroGeneroEnElTituloSeReemplazaYNoSeRepite() {
    assertEquals(
        "Tenis estilo Adidas para caballero",
        TituloPorGenero.para("Tenis estilo Adidas para dama", "caballero"));
    assertEquals("Tenis importado para dama", TituloPorGenero.para("Tenis importado", "dama"));
  }

  @Test
  void paraDamaYCaballeroEsUnisex() {
    assertEquals(
        "Tenis estilo Nike para caballero",
        TituloPorGenero.para("Tenis estilo Nike para dama y caballero", "caballero"));
    assertEquals(
        "Tenis importado para dama",
        TituloPorGenero.para("Tenis importado para hombre y mujer", "dama"));
  }

  @Test
  void sinTituloNoHayTitulo() {
    assertNull(TituloPorGenero.para(null, "dama"));
    assertNull(TituloPorGenero.para(" ", "dama"));
  }
}
