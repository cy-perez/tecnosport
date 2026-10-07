package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarMarcas;
import co.tecnosport.api.application.catalogo.MarcaConLineas;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    repositorio.conMarcasConProductosPublicados(Marca.crear("TecnoSport"));

    mockMvc
        .perform(get("/api/v1/marcas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].nombre").value("TecnoSport"))
        .andExpect(jsonPath("$.cursorSiguiente").isEmpty());
  }

  /**
   * Las líneas de cada marca viajan en la respuesta, y en el orden del enum y no en el que las
   * devolvió la base. Es lo que el filtro necesita para acotar el desplegable de marcas cuando la
   * URL trae {@code ?linea=}: sin esto ofrecía todas y elegir una llevaba a una rejilla vacía.
   */
  @Test
  void cadaMarcaDiceEnQueLineasTieneAlgoPublicado() throws Exception {
    repositorio.conMarcaEnLineas(Marca.crear("Nike"), LineaCatalogo.ROPA, LineaCatalogo.CALZADO);

    mockMvc
        .perform(get("/api/v1/marcas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].lineas", hasSize(2)))
        .andExpect(jsonPath("$.items[0].lineas[0]").value("ROPA"))
        .andExpect(jsonPath("$.items[0].lineas[1]").value("CALZADO"));
  }

  /**
   * El endpoint público es el filtro de la vitrina: una marca que existe pero no tiene nada
   * publicado no puede salir por aquí, porque el filtro llevaría a una rejilla vacía.
   */
  @Test
  void noDevuelveUnaMarcaQueExistePeroNoTieneProductosPublicados() throws Exception {
    repositorio.conMarcas(Marca.crear("Bose"));
    repositorio.conMarcasConProductosPublicados();

    mockMvc
        .perform(get("/api/v1/marcas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)));
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

    private List<Marca> todas = List.of();
    private List<MarcaConLineas> conProductos = List.of();

    void conMarcas(Marca... marcas) {
      this.todas = List.of(marcas);
    }

    void conMarcasConProductosPublicados(Marca... marcas) {
      this.conProductos =
          List.of(marcas).stream()
              .map(marca -> new MarcaConLineas(marca, Set.of(LineaCatalogo.TECNOLOGIA)))
              .toList();
    }

    void conMarcaEnLineas(Marca marca, LineaCatalogo... lineas) {
      List<MarcaConLineas> nuevas = new ArrayList<>(conProductos);
      nuevas.add(new MarcaConLineas(marca, Set.of(lineas)));
      this.conProductos = List.copyOf(nuevas);
    }

    @Override
    public List<Marca> listarTodas() {
      return todas;
    }

    @Override
    public List<MarcaConLineas> listarConProductosPublicados() {
      return conProductos;
    }

    @Override
    public boolean existeConNombre(String nombre) {
      return todas.stream().anyMatch(marca -> marca.nombre().equalsIgnoreCase(nombre));
    }

    /** Este controlador no crea marcas; aquí solo cumple el contrato del puerto. */
    @Override
    public void guardar(Marca marca) {
      throw new UnsupportedOperationException("Este doble no guarda marcas.");
    }

    @Override
    public Optional<Marca> buscarPorId(UUID id) {
      return todas.stream().filter(marca -> marca.id().equals(id)).findFirst();
    }
  }
}
