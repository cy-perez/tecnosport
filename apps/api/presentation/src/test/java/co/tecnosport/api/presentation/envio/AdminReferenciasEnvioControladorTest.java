package co.tecnosport.api.presentation.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.envio.ConsultarReferenciasDeEnvio;
import co.tecnosport.api.application.envio.FijarMedidasDeReferencia;
import co.tecnosport.api.application.envio.FijarPesoDeReferencia;
import co.tecnosport.api.application.envio.QuitarPesoDeReferencia;
import co.tecnosport.api.application.envio.RepositorioReferenciasDeEnvio;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Los pesos y las medidas de referencia por HTTP (adr/0071): qué nombres salen, que una categoría
 * de tecnología o una rama no admiten peso —409—, y que un cuerpo incompleto o un cero no pasan.
 */
@WebMvcTest(AdminReferenciasEnvioControlador.class)
@Import(AdminReferenciasEnvioControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminReferenciasEnvioControladorTest {

  private static final String RUTA = "/api/v1/admin/envios/referencias";

  private static final Categoria ROPA_DAMA =
      Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
  private static final Categoria JEANS =
      Categoria.crearBajo(ROPA_DAMA, "Jeans", new Slug("ropa-dama-jeans"));
  private static final Categoria UNISEX =
      Categoria.crear("Unisex", new Slug("calzado-unisex"), LineaCatalogo.CALZADO);
  private static final Categoria AUDIFONOS =
      Categoria.crear("Audífonos", new Slug("audifonos"), LineaCatalogo.TECNOLOGIA);

  @Autowired private MockMvc mockMvc;
  @Autowired private ReferenciasEnMemoria referencias;

  @BeforeEach
  void autenticarComoAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void la_consulta_trae_las_medidas_y_las_hojas_con_su_peso() throws Exception {
    referencias.guardarMedidas(new MedidasDeReferencia(40, 30, 10));
    referencias.guardarPeso(new PesoDeReferencia(JEANS.id(), 700));

    mockMvc
        .perform(get(RUTA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.medidas.largoCm").value(40))
        .andExpect(jsonPath("$.medidas.anchoCm").value(30))
        .andExpect(jsonPath("$.medidas.altoCm").value(10))
        .andExpect(jsonPath("$.categorias.length()").value(2))
        .andExpect(jsonPath("$.categorias[0].nombre").value("Jeans"))
        .andExpect(jsonPath("$.categorias[0].rama").value("Dama"))
        .andExpect(jsonPath("$.categorias[0].linea").value("ROPA"))
        .andExpect(jsonPath("$.categorias[0].categoriaId").value(JEANS.id().toString()))
        .andExpect(jsonPath("$.categorias[0].pesoGramos").value(700))
        .andExpect(jsonPath("$.categorias[1].nombre").value("Unisex"))
        .andExpect(jsonPath("$.categorias[1].pesoGramos").doesNotExist());
  }

  @Test
  void sin_medidas_fijadas_la_consulta_las_trae_nulas() throws Exception {
    mockMvc
        .perform(get(RUTA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.medidas").doesNotExist());
  }

  @Test
  void fijar_las_medidas_las_guarda() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/medidas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"largoCm\":40,\"anchoCm\":30,\"altoCm\":5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.altoCm").value(5));

    assertEquals(Optional.of(new MedidasDeReferencia(40, 30, 5)), referencias.medidas());
  }

  @Test
  void unas_medidas_en_cero_son_422() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/medidas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"largoCm\":40,\"anchoCm\":30,\"altoCm\":0}"))
        .andExpect(status().isUnprocessableContent());

    assertTrue(referencias.medidas().isEmpty());
  }

  @Test
  void unas_medidas_incompletas_son_422() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/medidas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"largoCm\":40,\"anchoCm\":30}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void fijar_un_peso_lo_guarda() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/pesos/" + JEANS.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pesoGramos\":650}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categoriaId").value(JEANS.id().toString()))
        .andExpect(jsonPath("$.pesoGramos").value(650));

    assertEquals(Integer.valueOf(650), referencias.pesoDe(JEANS.id()));
  }

  @Test
  void la_tecnologia_no_admite_peso_y_es_409() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/pesos/" + AUDIFONOS.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pesoGramos\":300}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PESO_DE_REFERENCIA_NO_ADMITIDO"));
  }

  @Test
  void una_rama_no_admite_peso_y_es_409() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/pesos/" + ROPA_DAMA.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pesoGramos\":300}"))
        .andExpect(status().isConflict());
  }

  @Test
  void una_categoria_que_no_existe_es_404() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/pesos/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pesoGramos\":300}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void un_peso_en_cero_es_422() throws Exception {
    mockMvc
        .perform(
            put(RUTA + "/pesos/" + JEANS.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pesoGramos\":0}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void quitar_un_peso_es_204_y_lo_borra() throws Exception {
    referencias.guardarPeso(new PesoDeReferencia(JEANS.id(), 700));

    mockMvc.perform(delete(RUTA + "/pesos/" + JEANS.id())).andExpect(status().isNoContent());

    assertEquals(null, referencias.pesoDe(JEANS.id()));
  }

  /** Las referencias en memoria. */
  static final class ReferenciasEnMemoria implements RepositorioReferenciasDeEnvio {

    private MedidasDeReferencia medidas;
    private final Map<UUID, Integer> pesos = new LinkedHashMap<>();

    Integer pesoDe(UUID categoriaId) {
      return pesos.get(categoriaId);
    }

    @Override
    public Optional<MedidasDeReferencia> medidas() {
      return Optional.ofNullable(medidas);
    }

    @Override
    public void guardarMedidas(MedidasDeReferencia medidas) {
      this.medidas = medidas;
    }

    @Override
    public List<PesoDeReferencia> pesos() {
      List<PesoDeReferencia> todos = new ArrayList<>();
      pesos.forEach((categoria, gramos) -> todos.add(new PesoDeReferencia(categoria, gramos)));
      return todos;
    }

    @Override
    public void guardarPeso(PesoDeReferencia peso) {
      pesos.put(peso.categoriaId(), peso.pesoGramos());
    }

    @Override
    public void quitarPeso(UUID categoriaId) {
      pesos.remove(categoriaId);
    }
  }

  /** El árbol de la prueba, de solo lectura: estos casos de uso no escriben categorías. */
  record CategoriasEnMemoria(List<Categoria> todas) implements RepositorioCategorias {

    @Override
    public List<Categoria> listarTodas() {
      return todas;
    }

    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
      return todas.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Categoria> buscarPorSlug(Slug slug) {
      return todas.stream().filter(categoria -> categoria.slug().equals(slug)).findFirst();
    }

    @Override
    public List<Categoria> hijasDe(UUID padreId) {
      return todas.stream()
          .filter(categoria -> categoria.padreId().map(padreId::equals).orElse(false))
          .toList();
    }

    @Override
    public void guardar(Categoria categoria) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void eliminar(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean tieneProductos(UUID categoriaId) {
      return false;
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    ReferenciasEnMemoria referenciasEnMemoria() {
      return new ReferenciasEnMemoria();
    }

    @Bean
    RepositorioCategorias repositorioCategorias() {
      return new CategoriasEnMemoria(List.of(ROPA_DAMA, JEANS, UNISEX, AUDIFONOS));
    }

    @Bean
    ConsultarReferenciasDeEnvio consultarReferenciasDeEnvio(
        ReferenciasEnMemoria referencias, RepositorioCategorias categorias) {
      return new ConsultarReferenciasDeEnvio(referencias, categorias);
    }

    @Bean
    FijarMedidasDeReferencia fijarMedidasDeReferencia(ReferenciasEnMemoria referencias) {
      return new FijarMedidasDeReferencia(referencias);
    }

    @Bean
    FijarPesoDeReferencia fijarPesoDeReferencia(
        ReferenciasEnMemoria referencias, RepositorioCategorias categorias) {
      return new FijarPesoDeReferencia(referencias, categorias);
    }

    @Bean
    QuitarPesoDeReferencia quitarPesoDeReferencia(ReferenciasEnMemoria referencias) {
      return new QuitarPesoDeReferencia(referencias);
    }

    @Bean
    PlatformTransactionManager platformTransactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }
}
