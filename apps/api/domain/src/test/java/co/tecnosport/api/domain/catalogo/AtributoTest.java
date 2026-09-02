package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
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
}
