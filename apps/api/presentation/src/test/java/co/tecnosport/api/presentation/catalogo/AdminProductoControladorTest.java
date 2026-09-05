package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminProductoControlador.class)
@Import(AdminProductoControladorTest.Configuracion.class)
class AdminProductoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba repositorio;

  @Test
  void listaProductosEnBorradorYPublicadosConPaginacion() throws Exception {
    Producto borrador = productoEnBorrador();
    repositorio.devolverEnBusquedaAdmin(new ProductosPaginados(List.of(borrador), 0, 2, 3));

    mockMvc
        .perform(get("/api/v1/admin/productos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(borrador.id().toString()))
        .andExpect(jsonPath("$.items[0].estado").value("BORRADOR"))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.totalPaginas").value(2))
        .andExpect(jsonPath("$.totalProductos").value(3));
  }

  @Test
  void listaVaciaDevuelve200ConItemsVacios() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/productos").param("pagina", "0").param("tamano", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)));
  }

  private static Producto productoEnBorrador() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
    assert producto.estado() == EstadoProducto.BORRADOR;
    return producto;
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    ListarProductosAdmin listarProductosAdmin(RepositorioProductos repositorio) {
      return new ListarProductosAdmin(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }

    @Bean
    MapeadorRespuestasProductoAdmin mapeadorRespuestasProductoAdmin(
        MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo) {
      return new MapeadorRespuestasProductoAdmin(mapeadorRespuestasCatalogo);
    }
  }
}
