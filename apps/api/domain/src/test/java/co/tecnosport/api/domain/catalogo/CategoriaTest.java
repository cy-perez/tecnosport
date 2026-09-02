package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import org.junit.jupiter.api.Test;

class CategoriaTest {

  @Test
  void guardaLaLineaDelCatalogo() {
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);

    assertEquals(LineaCatalogo.BOLSOS, categoria.linea());
  }

  @Test
  void rechazaNombreVacio() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> Categoria.crear(" ", new Slug("x"), LineaCatalogo.CELULARES));
  }
}
