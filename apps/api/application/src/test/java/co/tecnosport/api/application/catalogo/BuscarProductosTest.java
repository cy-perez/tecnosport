package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuscarProductosTest {

  @Test
  void delegaElComandoAlPuertoYDevuelveElResultadoTalCual() {
    RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
    ResultadoPaginado<Producto> esperado =
        new ResultadoPaginado<>(List.of(productoDePrueba()), "cursor-2");
    repositorio.devolverEnBusqueda(esperado);

    FiltroProductos filtro =
        new FiltroProductos(null, null, LineaCatalogo.BOLSOS, 50_000L, 200_000L, "morral");
    BuscarProductosComando comando =
        new BuscarProductosComando(filtro, OrdenProductos.PRECIO_ASC, "cursor-1", 20);

    ResultadoPaginado<Producto> resultado = new BuscarProductos(repositorio).ejecutar(comando);

    assertSame(esperado, resultado);
    assertEquals(filtro, repositorio.ultimoFiltro);
    assertEquals(OrdenProductos.PRECIO_ASC, repositorio.ultimoOrden);
    assertEquals("cursor-1", repositorio.ultimoCursor);
    assertEquals(20, repositorio.ultimoTamanoPagina);
  }

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    return Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
  }
}
