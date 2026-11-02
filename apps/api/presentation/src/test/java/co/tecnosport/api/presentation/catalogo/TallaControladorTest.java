package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.ConsultaDeTallasEnExistencia;
import co.tecnosport.api.application.catalogo.ListarTallasDisponibles;
import co.tecnosport.api.application.catalogo.TallaEnExistencia;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TallaControlador.class)
@Import(TallaControladorTest.Configuracion.class)
class TallaControladorTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void devuelveLasTallasDeCadaLineaConSuValorYSuEtiqueta() throws Exception {
    mockMvc
        .perform(get("/api/v1/tallas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ropa", hasSize(2)))
        .andExpect(jsonPath("$.ropa[0].valor").value("M"))
        .andExpect(jsonPath("$.ropa[1].valor").value("UNICA"))
        .andExpect(jsonPath("$.ropa[1].etiqueta").value("Única"))
        .andExpect(jsonPath("$.calzado", hasSize(1)))
        .andExpect(jsonPath("$.calzado[0].valor").value("38"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    ConsultaDeTallasEnExistencia consultaDeTallas() {
      return () ->
          List.of(
              new TallaEnExistencia(LineaCatalogo.ROPA, "Única"),
              new TallaEnExistencia(LineaCatalogo.ROPA, "M"),
              new TallaEnExistencia(LineaCatalogo.CALZADO, "38"));
    }

    @Bean
    ListarTallasDisponibles listarTallasDisponibles(ConsultaDeTallasEnExistencia consulta) {
      return new ListarTallasDisponibles(consulta);
    }
  }
}
