package co.tecnosport.api.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaludControlador.class)
class SaludControladorTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void respondeDoscientos() throws Exception {
    mockMvc.perform(get("/api/v1/salud")).andExpect(status().isOk());
  }
}
