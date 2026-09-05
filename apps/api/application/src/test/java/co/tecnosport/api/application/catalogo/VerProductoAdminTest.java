package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerProductoAdminTest {

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final VerProductoAdmin verProductoAdmin = new VerProductoAdmin(repositorio);

  @Test
  void devuelveElProductoEncontradoPorId() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
    repositorio.conProductos(producto);

    Producto encontrado = verProductoAdmin.ejecutar(producto.id());

    assertSame(producto, encontrado);
  }

  @Test
  void lanzaProductoNoEncontradoPorIdSiNoExiste() {
    UUID id = UUID.randomUUID();

    assertThrows(ProductoNoEncontradoPorIdException.class, () -> verProductoAdmin.ejecutar(id));
  }
}
