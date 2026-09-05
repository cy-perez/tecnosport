package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarMarcas;
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

@WebMvcTest(MarcaControlador.class)
@Import(MarcaControladorTest.Configuracion.class)
class MarcaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioMarcasDobleDePrueba repositorio;

  @Test
  void listadoDevuelveItemsYCursorSiguienteNulo() throws Exception {
    repositorio.conMarcas(Marca.crear("TecnoSport"));

    mockMvc
        .perform(get("/api/v1/marcas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].nombre").value("TecnoSport"))
        .andExpect(jsonPath("$.cursorSiguiente").isEmpty());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioMarcasDobleDePrueba repositorioMarcas() {
      return new RepositorioMarcasDobleDePrueba();
    }

    @Bean
    ListarMarcas listarMarcas(RepositorioMarcas repositorio) {
      return new ListarMarcas(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
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
}
