package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarMarcasAdmin;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminMarcaControlador.class)
@Import(AdminMarcaControladorTest.Configuracion.class)
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

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioMarcasDobleDePrueba repositorioMarcas() {
      return new RepositorioMarcasDobleDePrueba();
    }

    @Bean
    ListarMarcasAdmin listarMarcasAdmin(RepositorioMarcas repositorio) {
      return new ListarMarcasAdmin(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }

  static class RepositorioMarcasDobleDePrueba implements RepositorioMarcas {

    private List<Marca> todas = List.of();
    private List<Marca> conProductos = List.of();

    void conMarcas(Marca... marcas) {
      this.todas = List.of(marcas);
    }

    void conMarcasConProductosPublicados(Marca... marcas) {
      this.conProductos = List.of(marcas);
    }

    @Override
    public List<Marca> listarTodas() {
      return todas;
    }

    @Override
    public List<Marca> listarConProductosPublicados() {
      return conProductos;
    }

    @Override
    public Optional<Marca> buscarPorId(UUID id) {
      return todas.stream().filter(marca -> marca.id().equals(id)).findFirst();
    }
  }
}
