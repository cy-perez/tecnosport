package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarCategorias;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoriaControlador.class)
@Import(CategoriaControladorTest.Configuracion.class)
class CategoriaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioCategoriasDobleDePrueba repositorio;

  @Test
  void listadoDevuelveItemsYCursorSiguienteNulo() throws Exception {
    repositorio.conCategorias(Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS));

    mockMvc
        .perform(get("/api/v1/categorias"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].nombre").value("Bolsos"))
        .andExpect(jsonPath("$.items[0].linea").value("BOLSOS"))
        .andExpect(jsonPath("$.cursorSiguiente").isEmpty());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioCategoriasDobleDePrueba repositorioCategorias() {
      return new RepositorioCategoriasDobleDePrueba();
    }

    @Bean
    ListarCategorias listarCategorias(RepositorioCategorias repositorio) {
      return new ListarCategorias(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
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
