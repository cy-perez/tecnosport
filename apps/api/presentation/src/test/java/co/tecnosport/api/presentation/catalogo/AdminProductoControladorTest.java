package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipal;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
  @Autowired private AlmacenDeImagenesDobleDePrueba almacenDeImagenes;

  // El bean del doble es un singleton compartido por Spring entre los métodos de esta clase de
  // prueba: sin esto, un producto sembrado por una prueba (p. ej. con slug "morral-urbano") queda
  // visible en la siguiente y CrearProducto le agrega un sufijo "-2" al creer que ya existe.
  @BeforeEach
  void limpiarRepositorio() {
    repositorio.limpiar();
    almacenDeImagenes.limpiar();
  }

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

  @Test
  void verDevuelve200ConElProducto() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", producto.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Morral urbano"))
        .andExpect(jsonPath("$.marca.id").value(producto.marca().id().toString()))
        .andExpect(jsonPath("$.categoria.id").value(producto.categoria().id().toString()));
  }

  @Test
  void verConIdInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void editarDevuelve200ConLosDatosActualizadosSinCambiarElSlug() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    Marca nuevaMarca = Marca.crear("Under Trail");
    Categoria nuevaCategoria =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.CELULARES);
    repositorioMarcas.conMarcas(nuevaMarca);
    repositorioCategorias.conCategorias(nuevaCategoria);

    mockMvc
        .perform(
            patch("/api/v1/admin/productos/{id}", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Morral renovado","descripcion":"Nueva","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(nuevaMarca.id(), nuevaCategoria.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Morral renovado"))
        .andExpect(jsonPath("$.slug").value("morral-urbano"))
        .andExpect(jsonPath("$.marca.nombre").value("Under Trail"));
  }

  @Test
  void editarConIdInexistenteDevuelve404() throws Exception {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            patch("/api/v1/admin/productos/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Nombre","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(marca.id(), categoria.id())))
        .andExpect(status().isNotFound());
  }

  @Test
  void editarConNombreVacioDevuelve422() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    repositorioMarcas.conMarcas(producto.marca());
    repositorioCategorias.conCategorias(producto.categoria());

    mockMvc
        .perform(
            patch("/api/v1/admin/productos/{id}", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(producto.marca().id(), producto.categoria().id())))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void solicitarUrlDeSubidaDevuelve201ConUrlYObjectKeyDelProducto() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal/url-subida", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/webp"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.objectKey", startsWith("productos/" + producto.id() + "/")))
        .andExpect(jsonPath("$.url", containsString("storage.googleapis.com")));
  }

  @Test
  void solicitarUrlDeSubidaConProductoInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal/url-subida", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/webp"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void confirmarImagenPrincipalDevuelve200ConLaImagenConfirmada() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKey, 45_000);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"objectKey":"%s","ancho":1000,"alto":800,"hash":"%s","altEs":"alt es","altEn":"alt en"}
                    """
                        .formatted(objectKey, "%064x".formatted(1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ancho").value(1000))
        .andExpect(jsonPath("$.alto").value(800));
  }

  @Test
  void confirmarImagenPrincipalConObjetoInexistenteEnElAlmacenDevuelve404() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-nunca-subido.webp";

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"objectKey":"%s","ancho":1000,"alto":800,"hash":"%s","altEs":"alt es","altEn":"alt en"}
                    """
                        .formatted(objectKey, "%064x".formatted(1))))
        .andExpect(status().isNotFound());
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
    VerProductoAdmin verProductoAdmin(RepositorioProductos repositorioProductos) {
      return new VerProductoAdmin(repositorioProductos);
    }

    @Bean
    EditarProducto editarProducto(
        RepositorioProductos repositorioProductos,
        RepositorioMarcas repositorioMarcas,
        RepositorioCategorias repositorioCategorias) {
      return new EditarProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
    }

    @Bean
    AlmacenDeImagenesDobleDePrueba almacenDeImagenes() {
      return new AlmacenDeImagenesDobleDePrueba();
    }

    @Bean
    SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new SolicitarSubidaDeImagenPrincipal(repositorioProductos, almacenDeImagenes);
    }

    @Bean
    ConfirmarImagenPrincipal confirmarImagenPrincipal(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new ConfirmarImagenPrincipal(repositorioProductos, almacenDeImagenes);
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
