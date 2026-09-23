package co.tecnosport.api.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los dos casos que el 23 de septiembre de 2026 salían como 500 contra el despliegue de dev, y que
 * no son errores nuestros sino peticiones mal formadas.
 *
 * <p>El daño de contestarlos con un 5xx no es de forma: las dos pasarelas reintentan ante 5xx, así
 * que una URL mal configurada se vuelve un bucle en vez de un fallo claro —pasó, con la ruta de
 * confirmación de Sistecrédito apuntada al vacío a propósito para otra prueba—; las alertas de 5xx
 * que {@code docs/07} promete para producción se llenan de ruido; y cada petición inventada deja
 * una traza en los registros.
 */
@WebMvcTest(SaludControlador.class)
@Import(ManejadorDeErroresTest.Configuracion.class)
class ManejadorDeErroresTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unaRutaQueNoExisteEsUn404YNoUnErrorNuestro() throws Exception {
    mockMvc
        .perform(get("/api/v1/ruta-inventada"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("RUTA_NO_ENCONTRADA"));
  }

  /**
   * Se encontró pidiendo {@code GET /pedidos/{id}/seguimiento} sin {@code correo}. Con el parámetro
   * puesto y un pedido inexistente la respuesta ya era el 404 correcto, así que lo único que
   * fallaba era la falta del parámetro.
   */
  @Test
  void unParametroObligatorioQueFaltaEsUnaSolicitudInvalida() throws Exception {
    mockMvc
        .perform(get("/api/v1/prueba-de-errores/con-parametro"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.title").value("Solicitud inválida"));
  }

  /** Con el parámetro, la misma ruta responde bien: la guarda no se come lo que sí está. */
  @Test
  void conElParametroLaMismaRutaResponde() throws Exception {
    mockMvc
        .perform(get("/api/v1/prueba-de-errores/con-parametro").param("correo", "a@tecnosport.co"))
        .andExpect(status().isOk());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    ControladorDePrueba controladorDePrueba() {
      return new ControladorDePrueba();
    }
  }

  /**
   * Un controlador de mentira y no uno de verdad: lo que se prueba es el manejador, y colgarse de
   * un endpoint real ataría esta prueba a los casos de uso que ese endpoint necesite construir.
   */
  @RestController
  static class ControladorDePrueba {

    @GetMapping("/api/v1/prueba-de-errores/con-parametro")
    String conParametro(@RequestParam String correo) {
      return correo;
    }
  }
}
