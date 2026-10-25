package co.tecnosport.api.presentation.pedido;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.ConsultarEnlaceDeCompra;
import co.tecnosport.api.application.pedido.RepositorioEnlacesDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnlaceDeCompraControlador.class)
@Import(EnlaceDeCompraControladorTest.Configuracion.class)
class EnlaceDeCompraControladorTest {

  private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioEnlacesDeCompraDobleDePrueba enlaces;

  @Test
  void unEnlaceVigenteDiceSuVarianteYHastaCuando() throws Exception {
    UUID varianteId = UUID.randomUUID();
    EnlaceDeCompra enlace =
        EnlaceDeCompra.generar(varianteId, UUID.randomUUID(), Duration.ofHours(24), AHORA);
    enlaces.guardar(enlace);

    mockMvc
        .perform(get("/api/v1/enlaces-de-compra/{token}", enlace.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.varianteId").value(varianteId.toString()))
        .andExpect(jsonPath("$.expiraEn").value("2026-10-25T15:00:00Z"));
  }

  @Test
  void unoQueNoSirveRespondeIgualQueUnoQueNoExiste() throws Exception {
    EnlaceDeCompra usado =
        EnlaceDeCompra.generar(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(24), AHORA);
    usado.usar(UUID.randomUUID(), AHORA);
    enlaces.guardar(usado);

    mockMvc
        .perform(get("/api/v1/enlaces-de-compra/{token}", usado.token()))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.codigo").value("ENLACE_DE_COMPRA_NO_VIGENTE"));
    mockMvc
        .perform(get("/api/v1/enlaces-de-compra/{token}", "inventado"))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.codigo").value("ENLACE_DE_COMPRA_NO_VIGENTE"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioEnlacesDeCompraDobleDePrueba repositorioEnlaces() {
      return new RepositorioEnlacesDeCompraDobleDePrueba();
    }

    @Bean
    ConsultarEnlaceDeCompra consultarEnlaceDeCompra(RepositorioEnlacesDeCompra enlaces) {
      Reloj reloj = () -> AHORA;
      return new ConsultarEnlaceDeCompra(enlaces, reloj);
    }
  }
}
