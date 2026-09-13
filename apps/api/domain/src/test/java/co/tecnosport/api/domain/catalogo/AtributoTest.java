package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AtributoTest {

  @Test
  void valoresPermitidosVacioSignificaLibre() {
    Atributo material = Atributo.crear("Material", TipoAtributo.TEXTO, null);

    assertEquals(List.of(), material.valoresPermitidos());
  }

  @Test
  void rechazaNombreVacio() {
    assertThrows(
        ExcepcionDeDominio.class, () -> Atributo.crear(" ", TipoAtributo.TEXTO, List.of()));
  }

  @Test
  void conservaLaUnidadRecortada() {
    Atributo garantia =
        new Atributo(UUID.randomUUID(), "Garantía", TipoAtributo.NUMERO, List.of(), " meses ");

    assertEquals("meses", garantia.unidad().orElseThrow());
  }

  /** "12 " con una unidad en blanco sería peor que "12": en blanco es no tener unidad. */
  @Test
  void unaUnidadEnBlancoEsNoTenerUnidad() {
    Atributo talla = new Atributo(UUID.randomUUID(), "Talla", TipoAtributo.TEXTO, List.of(), "  ");

    assertTrue(talla.unidad().isEmpty());
    assertTrue(
        new Atributo(UUID.randomUUID(), "Talla", TipoAtributo.TEXTO, List.of()).unidad().isEmpty());
  }
}
