package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarCategoriasAdmin;
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

@WebMvcTest(AdminCategoriaControlador.class)
@Import(AdminCategoriaControladorTest.Configuracion.class)
class AdminCategoriaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioCategoriasDobleDePrueba repositorio;

  /** Sin esto no habría forma de cargar el primer proyector desde el panel. */
  @Test
  void devuelveTambienLasCategoriasSinProductosPublicados() throws Exception {
    repositorio.conCategorias(
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA),
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA));
    repositorio.conCategoriasConProductosPublicados(
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA));

    mockMvc
        .perform(get("/api/v1/admin/categorias"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[1].nombre").value("Proyectores"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioCategoriasDobleDePrueba repositorioCategorias() {
      return new RepositorioCategoriasDobleDePrueba();
    }

    @Bean
    ListarCategoriasAdmin listarCategoriasAdmin(RepositorioCategorias repositorio) {
      return new ListarCategoriasAdmin(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }

  static class RepositorioCategoriasDobleDePrueba implements RepositorioCategorias {

    private List<Categoria> todas = List.of();
    private List<Categoria> conProductos = List.of();

    void conCategorias(Categoria... categorias) {
      this.todas = List.of(categorias);
    }

    void conCategoriasConProductosPublicados(Categoria... categorias) {
      this.conProductos = List.of(categorias);
    }

    @Override
    public List<Categoria> listarTodas() {
      return todas;
    }

    @Override
    public List<Categoria> listarConProductosPublicados() {
      return conProductos;
    }

    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
      return todas.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
    }
  }
}
