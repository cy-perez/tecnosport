package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.envio.ListarCoberturaContraentrega;
import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoberturaContraentregaControlador.class)
@Import(CoberturaContraentregaControladorTest.Configuracion.class)
class CoberturaContraentregaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioCoberturaContraentregaDobleDePrueba cobertura;

  @Test
  void listaLasCiudadesCubiertas() throws Exception {
    cobertura.conCiudadCubierta("05001");

    mockMvc
        .perform(get("/api/v1/envios/cobertura"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("05001"));
  }

  @Test
  void listaVaciaSinCiudadesCubiertas() throws Exception {
    mockMvc
        .perform(get("/api/v1/envios/cobertura"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioCoberturaContraentregaDobleDePrueba repositorioCoberturaContraentrega() {
      return new RepositorioCoberturaContraentregaDobleDePrueba();
    }

    @Bean
    ListarCoberturaContraentrega listarCoberturaContraentrega(
        RepositorioCoberturaContraentrega repositorio) {
      return new ListarCoberturaContraentrega(repositorio);
    }
  }
}
