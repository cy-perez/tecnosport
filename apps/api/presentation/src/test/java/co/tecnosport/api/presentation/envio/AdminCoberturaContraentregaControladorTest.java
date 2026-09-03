package co.tecnosport.api.presentation.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.envio.AgregarCoberturaContraentrega;
import co.tecnosport.api.application.envio.QuitarCoberturaContraentrega;
import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import co.tecnosport.api.presentation.envio.dto.CoberturaContraentregaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCoberturaContraentregaControlador.class)
@Import(AdminCoberturaContraentregaControladorTest.Configuracion.class)
class AdminCoberturaContraentregaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioCoberturaContraentregaDobleDePrueba cobertura;

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void agregarCubreLaCiudad() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/cobertura-contraentrega")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CoberturaContraentregaRequest("05001"))))
        .andExpect(status().isNoContent());

    assertThat(cobertura.estaCubierta("05001")).isTrue();
  }

  @Test
  void quitarExcluyeLaCiudad() throws Exception {
    cobertura.conCiudadCubierta("05001");

    mockMvc
        .perform(delete("/api/v1/admin/cobertura-contraentrega/{codigo}", "05001"))
        .andExpect(status().isNoContent());

    assertThat(cobertura.estaCubierta("05001")).isFalse();
  }

  @Test
  void agregarConCodigoVacioDevuelve422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/cobertura-contraentrega")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoDaneCiudad\":\"\"}"))
        .andExpect(status().isUnprocessableContent());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioCoberturaContraentregaDobleDePrueba repositorioCoberturaContraentrega() {
      return new RepositorioCoberturaContraentregaDobleDePrueba();
    }

    @Bean
    AgregarCoberturaContraentrega agregarCoberturaContraentrega(
        RepositorioCoberturaContraentrega repositorio) {
      return new AgregarCoberturaContraentrega(repositorio);
    }

    @Bean
    QuitarCoberturaContraentrega quitarCoberturaContraentrega(
        RepositorioCoberturaContraentrega repositorio) {
      return new QuitarCoberturaContraentrega(repositorio);
    }
  }
}
