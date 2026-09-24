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
        .andExpect(jsonPath("$.items[0].padreId").isEmpty())
        .andExpect(jsonPath("$.cursorSiguiente").isEmpty());
  }

  /**
   * Esta prueba afirmaba lo contrario: que "Proyectores", viva desde {@code V38} y sin un producto
   * detrás, no podía salir por el filtro de la vitrina. Desde el 24 de septiembre de 2026 sale, y
   * el motivo está en {@code ListarCategorias}: con un menú que pinta el árbol, esconder una
   * categoría vacía es afirmar que no se vende eso.
   */
  @Test
  void devuelveUnaCategoriaQueExisteAunqueNoTengaProductos() throws Exception {
    repositorio.conCategorias(
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA));

    mockMvc
        .perform(get("/api/v1/categorias"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].nombre").value("Proyectores"));
  }

  /** El padre viaja en la respuesta: sin él, quien pinta el menú no puede colgar nada. */
  @Test
  void unaSubcategoriaViajaConElIdDeSuPadre() throws Exception {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, faldas);

    mockMvc
        .perform(get("/api/v1/categorias"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[1].nombre").value("Faldas"))
        .andExpect(jsonPath("$.items[1].padreId").value(dama.id().toString()))
        .andExpect(jsonPath("$.items[1].linea").value("ROPA"));
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
}
