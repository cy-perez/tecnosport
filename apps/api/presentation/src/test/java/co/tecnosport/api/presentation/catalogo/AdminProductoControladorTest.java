package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminProductoControlador.class)
@Import(AdminProductoControladorTest.Configuracion.class)
class AdminProductoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba repositorio;
  @Autowired private RepositorioMarcasDobleDePrueba repositorioMarcas;
  @Autowired private RepositorioCategoriasDobleDePrueba repositorioCategorias;

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

  @Test
  void crearDevuelve201ConElProductoEnBorrador() throws Exception {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            post("/api/v1/admin/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Morral Urbano","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(marca.id(), categoria.id())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Morral Urbano"))
        .andExpect(jsonPath("$.slug").value("morral-urbano"))
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
  }

  @Test
  void crearConMarcaInexistenteDevuelve404() throws Exception {
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            post("/api/v1/admin/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Morral Urbano","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(UUID.randomUUID(), categoria.id())))
        .andExpect(status().isNotFound());
  }

  @Test
  void crearConNombreVacioDevuelve422() throws Exception {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            post("/api/v1/admin/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(marca.id(), categoria.id())))
        .andExpect(status().isUnprocessableContent());
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
    RepositorioMarcasDobleDePrueba repositorioMarcas() {
      return new RepositorioMarcasDobleDePrueba();
    }

    @Bean
    RepositorioCategoriasDobleDePrueba repositorioCategorias() {
      return new RepositorioCategoriasDobleDePrueba();
    }

    @Bean
    ListarProductosAdmin listarProductosAdmin(RepositorioProductos repositorio) {
      return new ListarProductosAdmin(repositorio);
    }

    @Bean
    CrearProducto crearProducto(
        RepositorioProductos repositorioProductos,
        RepositorioMarcas repositorioMarcas,
        RepositorioCategorias repositorioCategorias) {
      return new CrearProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
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

  static class RepositorioMarcasDobleDePrueba implements RepositorioMarcas {

    private List<Marca> marcas = List.of();

    void conMarcas(Marca... marcas) {
      this.marcas = List.of(marcas);
    }

    @Override
    public List<Marca> listarTodas() {
      return marcas;
    }

    @Override
    public Optional<Marca> buscarPorId(UUID id) {
      return marcas.stream().filter(marca -> marca.id().equals(id)).findFirst();
    }
  }

  static class RepositorioCategoriasDobleDePrueba implements RepositorioCategorias {

    private List<Categoria> categorias = List.of();

    void conCategorias(Categoria... categorias) {
      this.categorias = List.of(categorias);
    }

    @Override
    public List<Categoria> listarTodas() {
      return categorias;
    }

    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
      return categorias.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
    }
  }
}
