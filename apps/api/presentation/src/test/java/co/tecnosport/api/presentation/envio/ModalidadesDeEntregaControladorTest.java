package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.pedido.ModalidadesDeEntrega;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** El checkout lee aquí si hay recogida; con la bandera apagada, no la ofrece. */
@WebMvcTest(ModalidadesDeEntregaControlador.class)
@Import(ModalidadesDeEntregaControladorTest.Configuracion.class)
class ModalidadesDeEntregaControladorTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void conLaRecogidaApagadaSoloOfreceElEnvioADomicilio() throws Exception {
    mockMvc
        .perform(get("/api/v1/envios/modalidades"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envioADomicilio").value(true))
        .andExpect(jsonPath("$.retiroEnPunto").value(false));
  }

  @TestConfiguration
  static class Configuracion {
    @Bean
    ModalidadesDeEntrega modalidadesDeEntrega() {
      return new ModalidadesDeEntrega(false);
    }
  }
}
