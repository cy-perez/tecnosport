package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.CrearCategoria;
import co.tecnosport.api.application.catalogo.EditarCategoria;
import co.tecnosport.api.application.catalogo.EliminarCategoria;
import co.tecnosport.api.application.catalogo.ListarCategorias;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCategoriaControlador.class)
@Import({AdminCategoriaControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminCategoriaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioCategoriasDobleDePrueba repositorio;

  @Test
  void listaTodoElArbol() throws Exception {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    repositorio.conCategorias(
        dama, Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas")));

    mockMvc
        .perform(get("/api/v1/admin/categorias"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)));
  }

  @Test
  void creaUnaCategoriaDePrimerNivel() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Impresoras\",\"linea\":\"TECNOLOGIA\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Impresoras"))
        .andExpect(jsonPath("$.slug").value("impresoras"))
        .andExpect(jsonPath("$.padreId").isEmpty());
  }

  /** El slug de una hija lleva el del padre delante: "Busos" existe en Dama y en Caballero. */
  @Test
  void creaUnaSubcategoriaConElSlugDelPadreDelante() throws Exception {
    Categoria caballero =
        Categoria.crear("Caballero", new Slug("ropa-caballero"), LineaCatalogo.ROPA);
    repositorio.conCategorias(caballero);

    mockMvc
        .perform(
            post("/api/v1/admin/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Busos\",\"padreId\":\"" + caballero.id() + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("ropa-caballero-busos"))
        .andExpect(jsonPath("$.linea").value("ROPA"))
        .andExpect(jsonPath("$.padreId").value(caballero.id().toString()));
  }

  @Test
  void rechazaUnTercerNivel() throws Exception {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    Categoria faldas = Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas"));
    repositorio.conCategorias(dama, faldas);

    mockMvc
        .perform(
            post("/api/v1/admin/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Largas\",\"padreId\":\"" + faldas.id() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("dos niveles")));
  }

  @Test
  void rechazaUnSlugRepetido() throws Exception {
    repositorio.conCategorias(
        Categoria.crear("Relojes", new Slug("relojes"), LineaCatalogo.TECNOLOGIA));

    mockMvc
        .perform(
            post("/api/v1/admin/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Relojes\",\"linea\":\"TECNOLOGIA\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Slug de categoría ya en uso"));
  }

  @Test
  void renombraSinTocarElSlug() throws Exception {
    Categoria consolas =
        Categoria.crear("Consolas", new Slug("consolas"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(consolas);

    mockMvc
        .perform(
            put("/api/v1/admin/categorias/" + consolas.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Consolas de videojuegos\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Consolas de videojuegos"))
        .andExpect(jsonPath("$.slug").value("consolas"));
  }

  @Test
  void borraUnaCategoriaVacia() throws Exception {
    Categoria proyectores =
        Categoria.crear("Proyectores", new Slug("proyectores"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(proyectores);

    mockMvc
        .perform(delete("/api/v1/admin/categorias/" + proyectores.id()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/admin/categorias")).andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  void noBorraUnaCategoriaConSubcategorias() throws Exception {
    Categoria dama = Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA);
    repositorio.conCategorias(
        dama, Categoria.crearBajo(dama, "Faldas", new Slug("ropa-dama-faldas")));

    mockMvc
        .perform(delete("/api/v1/admin/categorias/" + dama.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("La categoría tiene subcategorías"));
  }

  @Test
  void noBorraUnaCategoriaConProductos() throws Exception {
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    repositorio.conCategorias(celulares);
    repositorio.conProductosEn(celulares);

    mockMvc
        .perform(delete("/api/v1/admin/categorias/" + celulares.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("La categoría tiene productos"));
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
    CrearCategoria crearCategoria(RepositorioCategorias repositorio) {
      return new CrearCategoria(repositorio);
    }

    @Bean
    EditarCategoria editarCategoria(RepositorioCategorias repositorio) {
      return new EditarCategoria(repositorio);
    }

    @Bean
    EliminarCategoria eliminarCategoria(RepositorioCategorias repositorio) {
      return new EliminarCategoria(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }
}
