package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.CrearMarca;
import co.tecnosport.api.application.catalogo.EliminarMarca;
import co.tecnosport.api.application.catalogo.ListarMarcasAdmin;
import co.tecnosport.api.application.catalogo.MarcaConLineas;
import co.tecnosport.api.application.catalogo.RenombrarMarca;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@WebMvcTest(AdminMarcaControlador.class)
@Import({AdminMarcaControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminMarcaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioMarcasDobleDePrueba repositorio;

  /**
   * El espejo exacto de {@code MarcaControladorTest}: aquí la marca sin productos <b>sí</b> sale, y
   * si algún día dejara de salir, dar de alta el primer producto de una marca nueva volvería a ser
   * imposible desde el panel.
   */
  @Test
  void devuelveTambienLasMarcasSinProductosPublicados() throws Exception {
    repositorio.conMarcas(Marca.crear("Xiaomi"), Marca.crear("Bose"));
    repositorio.conMarcasConProductosPublicados(Marca.crear("Xiaomi"));

    mockMvc
        .perform(get("/api/v1/admin/marcas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[1].nombre").value("Bose"));
  }

  /**
   * El doble es un bean del contexto y lo comparten todas las pruebas de esta clase, así que esta
   * empieza vaciándolo: sin eso, lo que afirma al final depende del orden en que JUnit corra los
   * métodos.
   */
  @Test
  void crearDevuelve201ConLaMarcaYLaGuarda() throws Exception {
    repositorio.conMarcas();

    mockMvc
        .perform(
            post("/api/v1/admin/marcas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Huawei\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Huawei"))
        .andExpect(jsonPath("$.id").isNotEmpty());

    mockMvc
        .perform(get("/api/v1/admin/marcas"))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].nombre").value("Huawei"));
  }

  /** {@code 409} y no {@code 422}: el nombre es válido, lo que lo rechaza es el catálogo. */
  @Test
  void crearDevuelve409SiElNombreYaExisteAunqueCambienLasMayusculas() throws Exception {
    repositorio.conMarcas(Marca.crear("Xiaomi"));

    mockMvc
        .perform(
            post("/api/v1/admin/marcas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"xiaomi\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void crearDevuelve422SiElNombreVieneVacio() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/marcas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"   \"}"))
        .andExpect(status().isUnprocessableContent());
  }

  /**
   * Sin el campo, no con el campo vacío. Jackson 3 no revienta aquí —{@code nombre} es de tipo
   * referencia y llega en nulo, el matiz del 18 de septiembre en {@code apps/api/CLAUDE.md}—, así
   * que quien tiene que rechazarlo es el dominio.
   */
  @Test
  void crearDevuelve422SiFaltaElCampoNombre() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/marcas").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void crearDevuelve422SiElNombrePasaElLargoDeLaColumna() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/marcas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + "X".repeat(Marca.LARGO_MAXIMO_NOMBRE + 1) + "\"}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void editarDevuelveLaMarcaConSuNombreNuevo() throws Exception {
    Marca xaomi = Marca.crear("Xaomi");
    repositorio.conMarcas(xaomi);

    mockMvc
        .perform(
            put("/api/v1/admin/marcas/{id}", xaomi.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Xiaomi\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(xaomi.id().toString()))
        .andExpect(jsonPath("$.nombre").value("Xiaomi"));

    mockMvc
        .perform(get("/api/v1/admin/marcas"))
        .andExpect(jsonPath("$.items[0].nombre").value("Xiaomi"));
  }

  @Test
  void editarConElNombreDeOtraEs409() throws Exception {
    Marca redmi = Marca.crear("Redmi");
    repositorio.conMarcas(Marca.crear("Xiaomi"), redmi);

    mockMvc
        .perform(
            put("/api/v1/admin/marcas/{id}", redmi.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"xiaomi\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("MARCA_YA_EXISTE"));
  }

  @Test
  void editarUnaQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/admin/marcas/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Xiaomi\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void eliminarSinProductosEs204() throws Exception {
    Marca sobra = Marca.crear("Xaomi");
    repositorio.conMarcas(sobra);

    mockMvc
        .perform(delete("/api/v1/admin/marcas/{id}", sobra.id()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/admin/marcas")).andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  void eliminarConProductosEs409YNoBorra() throws Exception {
    Marca xiaomi = Marca.crear("Xiaomi");
    repositorio.conMarcas(xiaomi);
    repositorio.productos.put(xiaomi.id(), 3L);

    mockMvc
        .perform(delete("/api/v1/admin/marcas/{id}", xiaomi.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("MARCA_CON_PRODUCTOS"));

    mockMvc.perform(get("/api/v1/admin/marcas")).andExpect(jsonPath("$.items", hasSize(1)));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RenombrarMarca renombrarMarca(RepositorioMarcas repositorio) {
      return new RenombrarMarca(repositorio);
    }

    @Bean
    EliminarMarca eliminarMarca(RepositorioMarcas repositorio) {
      return new EliminarMarca(repositorio);
    }

    /** Sin base: la transacción no hace nada, como en los demás controladores del panel. */
    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(
            Object transaction, org.springframework.transaction.TransactionDefinition definicion) {}

        @Override
        protected void doCommit(DefaultTransactionStatus estado) {}

        @Override
        protected void doRollback(DefaultTransactionStatus estado) {}
      };
    }

    @Bean
    RepositorioMarcasDobleDePrueba repositorioMarcas() {
      return new RepositorioMarcasDobleDePrueba();
    }

    @Bean
    ListarMarcasAdmin listarMarcasAdmin(RepositorioMarcas repositorio) {
      return new ListarMarcasAdmin(repositorio);
    }

    @Bean
    CrearMarca crearMarca(RepositorioMarcas repositorio) {
      return new CrearMarca(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }

  /**
   * Este sí guarda, a diferencia de sus dos hermanos: es el único controlador que crea marcas. Y
   * {@code existeConNombre} compara sin distinguir mayúsculas, como el índice de {@code V56} — un
   * doble que comparara exacto dejaría el {@code 409} sin probar.
   */
  static class RepositorioMarcasDobleDePrueba implements RepositorioMarcas {

    private final List<Marca> todas = new ArrayList<>();
    private List<Marca> conProductos = List.of();
    final Map<UUID, Long> productos = new HashMap<>();

    void conMarcas(Marca... marcas) {
      this.todas.clear();
      this.todas.addAll(List.of(marcas));
      this.productos.clear();
    }

    void conMarcasConProductosPublicados(Marca... marcas) {
      this.conProductos = List.of(marcas);
    }

    @Override
    public List<Marca> listarTodas() {
      return List.copyOf(todas);
    }

    @Override
    public List<MarcaConLineas> listarConProductosPublicados() {
      return conProductos.stream()
          .map(marca -> new MarcaConLineas(marca, Set.of(LineaCatalogo.TECNOLOGIA)))
          .toList();
    }

    @Override
    public Optional<Marca> buscarPorId(UUID id) {
      return todas.stream().filter(marca -> marca.id().equals(id)).findFirst();
    }

    @Override
    public boolean existeConNombre(String nombre) {
      return todas.stream().anyMatch(marca -> marca.nombre().equalsIgnoreCase(nombre));
    }

    @Override
    public void guardar(Marca marca) {
      todas.add(marca);
    }

    @Override
    public boolean existeOtraConNombre(String nombre, UUID excepto) {
      return todas.stream()
          .anyMatch(m -> !m.id().equals(excepto) && m.nombre().equalsIgnoreCase(nombre));
    }

    @Override
    public void actualizar(Marca marca) {
      todas.replaceAll(m -> m.id().equals(marca.id()) ? marca : m);
    }

    @Override
    public long contarProductos(UUID marcaId) {
      return productos.getOrDefault(marcaId, 0L);
    }

    @Override
    public void eliminar(Marca marca) {
      todas.removeIf(m -> m.id().equals(marca.id()));
    }
  }
}
