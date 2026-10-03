package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SinonimosDeBusquedaTest {

  @Test
  void bodyBuscaBodi() {
    assertEquals("bodi", SinonimosDeBusqueda.aplicar("body"));
    assertEquals("bodi herraje", SinonimosDeBusqueda.aplicar("Body herraje"));
    assertEquals("bodis negros", SinonimosDeBusqueda.aplicar("BODIES negros"));
    assertEquals("bodis", SinonimosDeBusqueda.aplicar("bodys"));
  }

  @Test
  void loDemasNoSeToca() {
    assertEquals("bodi herraje", SinonimosDeBusqueda.aplicar("bodi herraje"));
    assertEquals("bolso bodega", SinonimosDeBusqueda.aplicar("bolso bodega"));
    assertNull(SinonimosDeBusqueda.aplicar(null));
  }
}
