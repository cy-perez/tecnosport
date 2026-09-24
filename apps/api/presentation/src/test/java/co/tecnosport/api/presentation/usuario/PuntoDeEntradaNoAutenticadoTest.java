package co.tecnosport.api.presentation.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cuerpo de este 401 se arma concatenando cadenas —este punto corre antes del {@code
 * DispatcherServlet}, así que no hay serializador— y dentro va la URI de la petición, que la
 * escribe el cliente.
 *
 * <p>Hoy no es explotable: Tomcat rechaza las comillas crudas en la línea de petición y {@code
 * getRequestURI()} no decodifica {@code %22}. O sea que lo único que lo sostiene es una
 * configuración por omisión del contenedor, no el código — y {@code MockHttpServletRequest} sí deja
 * poner lo que sea, que es lo que permite probar la guarda de verdad.
 */
class PuntoDeEntradaNoAutenticadoTest {

  private final PuntoDeEntradaNoAutenticado punto = new PuntoDeEntradaNoAutenticado();
  private final JsonMapper json = JsonMapper.builder().build();

  private MockHttpServletResponse responderA(String uri) throws Exception {
    MockHttpServletRequest peticion = new MockHttpServletRequest("GET", uri);
    peticion.setRequestURI(uri);
    MockHttpServletResponse respuesta = new MockHttpServletResponse();
    punto.commence(peticion, respuesta, new InsufficientAuthenticationException("sin token"));
    return respuesta;
  }

  @Test
  void respondeUn401ConElCodigoQueElFrontendNecesita() throws Exception {
    MockHttpServletResponse respuesta = responderA("/api/v1/admin/productos");

    assertThat(respuesta.getStatus()).isEqualTo(401);
    JsonNode cuerpo = json.readTree(respuesta.getContentAsString());
    assertThat(cuerpo.path("codigo").asString()).isEqualTo("NO_AUTENTICADO");
    assertThat(cuerpo.path("instance").asString()).isEqualTo("/api/v1/admin/productos");
  }

  /**
   * La guarda. Sin escapar, la comilla parte el literal y el cuerpo deja de ser JSON: el frontend
   * no puede leer el {@code codigo}, y la renovación silenciosa del token —que cuelga justo de este
   * 401— caería a la rama genérica en todo el panel.
   */
  @Test
  void unaUriConComillasNoRompeElJson() throws Exception {
    MockHttpServletResponse respuesta = responderA("/api/v1/\",\"codigo\":\"INVENTADO");

    JsonNode cuerpo = json.readTree(respuesta.getContentAsString());
    assertThat(cuerpo.path("codigo").asString()).isEqualTo("NO_AUTENTICADO");
    assertThat(cuerpo.path("instance").asString()).isEqualTo("/api/v1/\",\"codigo\":\"INVENTADO");
  }

  @Test
  void unaUriConBarraInvertidaOSaltoDeLineaTampocoLoRompe() throws Exception {
    MockHttpServletResponse respuesta = responderA("/api/v1/a\\b\nc");

    JsonNode cuerpo = json.readTree(respuesta.getContentAsString());
    assertThat(cuerpo.path("codigo").asString()).isEqualTo("NO_AUTENTICADO");
    assertThat(cuerpo.path("instance").asString()).isEqualTo("/api/v1/a\\b\nc");
  }

  /** Y la tilde sigue llegando entera, que es lo que el charset explícito protege. */
  @Test
  void elDetalleConservaSusTildes() throws Exception {
    MockHttpServletResponse respuesta = responderA("/api/v1/admin");

    JsonNode cuerpo = json.readTree(respuesta.getContentAsString());
    assertThat(cuerpo.path("detail").asString()).isEqualTo("Esta ruta exige una sesión iniciada.");
  }
}
