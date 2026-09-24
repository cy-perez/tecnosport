package co.tecnosport.api.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.pago.SistecreditoNoRespondeException;
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

  /**
   * El mensaje de {@code MissingServletRequestParameterException} lo escribe el framework y habla
   * de nuestras clases: el tipo del parámetro, el nombre del método. Iba al {@code detail} tal
   * cual, así que cualquiera que mandara una petición incompleta recibía de vuelta estructura
   * interna. Lo que el frontend usa es el {@code codigo}, que no cambia.
   */
  @Test
  void unaSolicitudMalFormadaNoPublicaElDetalleInternoDelFramework() throws Exception {
    mockMvc
        .perform(get("/api/v1/prueba-de-errores/con-parametro"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("MISSING_SERVLET_REQUEST_PARAMETER"))
        .andExpect(jsonPath("$.detail").value(not(containsString("String"))))
        .andExpect(jsonPath("$.detail").value(not(containsString("parameter"))));
  }

  /**
   * La fuga que el manejador de al lado bloquea a conciencia citando la Ley 1266, y que salía
   * entera por este: el mensaje de esta excepción lleva concatenado el {@code message} del
   * proveedor para que el registro sirva, y {@code problema(...)} lo publicaba en el {@code
   * detail}.
   */
  @Test
  void elTextoCrudoDeSistecreditoNoLlegaAlComprador() throws Exception {
    mockMvc
        .perform(get("/api/v1/prueba-de-errores/sistecredito-caido"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("SISTECREDITO_NO_RESPONDE"))
        .andExpect(
            jsonPath("$.detail")
                .value(not(containsString("ya tiene una solicitud de credito en curso"))))
        .andExpect(jsonPath("$.detail").value(not(containsString("errorCode"))));
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

    /** Con el mensaje tal como lo compone `SistecreditoClient`, texto del proveedor incluido. */
    @GetMapping("/api/v1/prueba-de-errores/sistecredito-caido")
    String sistecreditoCaido() {
      throw new SistecreditoNoRespondeException(
          "Sistecredito rechazo la creacion: HTTP 400, errorCode=801, "
              + "message=El cliente ya tiene una solicitud de credito en curso");
    }
  }
}
