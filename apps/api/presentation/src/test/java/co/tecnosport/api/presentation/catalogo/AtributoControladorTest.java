package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ListarAtributos;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AtributoControlador.class)
@Import(AtributoControladorTest.Configuracion.class)
class AtributoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioAtributosDobleDePrueba repositorio;

  @Test
  void listadoDevuelveItemsYCursorSiguienteNulo() throws Exception {
    repositorio.conAtributos(Atributo.crear("Color", TipoAtributo.COLOR, List.of()));

    mockMvc
        .perform(get("/api/v1/atributos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].nombre").value("Color"))
        .andExpect(jsonPath("$.items[0].tipo").value("COLOR"))
        .andExpect(jsonPath("$.cursorSiguiente").isEmpty());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioAtributosDobleDePrueba repositorioAtributos() {
      return new RepositorioAtributosDobleDePrueba();
    }

    @Bean
    ListarAtributos listarAtributos(RepositorioAtributos repositorio) {
      return new ListarAtributos(repositorio);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }
}
