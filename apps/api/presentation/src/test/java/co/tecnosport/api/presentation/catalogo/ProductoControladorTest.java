package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.BuscarProductos;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VerFichaDeProducto;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductoControlador.class)
@Import(ProductoControladorTest.Configuracion.class)
class ProductoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba repositorio;

  @Test
  void fichaEncontradaDevuelve200ConRotacionNulaSiNoEstaPublicado() throws Exception {
    Producto producto = productoPublicado();
    repositorio.conProductos(producto);

    mockMvc
        .perform(get("/api/v1/productos/{slug}", producto.slug().valor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value(producto.nombre()))
        .andExpect(jsonPath("$.marca.nombre").value("TecnoSport"))
        .andExpect(jsonPath("$.variantes", hasSize(0)))
        .andExpect(jsonPath("$.rotacion").isEmpty());
  }

  @Test
  void slugInexistenteDevuelve404ConCodigoEstable() throws Exception {
    mockMvc
        .perform(get("/api/v1/productos/{slug}", "no-existe"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
  }

  @Test
  void ordenInvalidoDevuelve422() throws Exception {
    mockMvc
        .perform(get("/api/v1/productos").param("orden", "no-existe"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.campos").isArray());
  }

  @Test
  void tamanoFueraDeRangoDevuelve422() throws Exception {
    mockMvc
        .perform(get("/api/v1/productos").param("tamano", "999"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void listadoDevuelveItemsYCursorSiguiente() throws Exception {
    Producto producto = productoPublicado();
    repositorio.devolverEnBusqueda(new ResultadoPaginado<>(List.of(producto), "cursor-2"));

    mockMvc
        .perform(get("/api/v1/productos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.cursorSiguiente").value("cursor-2"));
  }

  private static Producto productoPublicado() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            "https://x/0.jpg",
            "https://x/0.webp",
            800,
            600,
            1000,
            new HashContenido("%064x".formatted(0)),
            "alt es",
            "alt en"));
    producto.publicar();
    return producto;
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    BuscarProductos buscarProductos(RepositorioProductos repositorio) {
      return new BuscarProductos(repositorio);
    }

    @Bean
    VerFichaDeProducto verFichaDeProducto(RepositorioProductos repositorio) {
      return new VerFichaDeProducto(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }
}
