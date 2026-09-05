package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListarProductosAdminTest {

  @Test
  void delegaElComandoAlPuertoYDevuelveElResultadoTalCual() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    ProductosPaginados esperado = new ProductosPaginados(List.of(productoDePrueba()), 1, 3, 45);
    repositorio.devolverEnBusquedaAdmin(esperado);

    ProductosPaginados resultado =
        new ListarProductosAdmin(repositorio).ejecutar(new ListarProductosAdminComando(1, 20));

    assertSame(esperado, resultado);
    assertEquals(1, repositorio.ultimaPaginaAdmin);
    assertEquals(20, repositorio.ultimoTamanoPaginaAdmin);
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }
}
