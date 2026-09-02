package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import org.junit.jupiter.api.Test;

class MarcaTest {

  @Test
  void rechazaNombreVacio() {
    assertThrows(ExcepcionDeDominio.class, () -> Marca.crear("  "));
  }
}
