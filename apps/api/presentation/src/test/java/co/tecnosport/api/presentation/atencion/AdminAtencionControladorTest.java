package co.tecnosport.api.presentation.atencion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.atencion.ListarSolicitudesDeAtencion;
import co.tecnosport.api.application.atencion.ProrrogarSolicitud;
import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.atencion.PlazosDeAtencion;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code @DirtiesContext} por el mismo motivo que los demas controladores del panel: los dobles son
 * beans singleton del contexto de prueba y arrastrarian estado entre metodos.
 */
@WebMvcTest(AdminAtencionControlador.class)
@Import(AdminAtencionControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminAtencionControladorTest {

  /** Jueves; el limite de diez habiles cae el 25 de septiembre y el de quince, el 2 de octubre. */
  private static final Instant AHORA = Instant.parse("2026-09-10T14:00:00Z");

  @Autowired private MockMvc mockMvc;

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  private void autenticarComoAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  private String cuerpoRadicar(String tipo) {
    return """
        {"tipo":"%s","correo":"cliente@tecnosport.co","asunto":"No me llego el pedido"}
        """
        .formatted(tipo);
  }

  @Test
  void radicarDevuelveElNumeroYElLimiteQueLeCorresponde() throws Exception {
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/atencion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoRadicar("PETICION")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.numeroRadicado").value("TS-PQR-2026-000001"))
        .andExpect(jsonPath("$.estado").value("RADICADA"))
        .andExpect(jsonPath("$.verdicto").value("EN_PLAZO"))
        .andExpect(jsonPath("$.limiteDeRespuesta").value("2026-10-02T05:00:00Z"));
  }

  /**
   * El mismo endpoint, el mismo dia, dos limites distintos. Es el hallazgo que hizo falta todo
   * esto: los terminos prometen quince dias habiles para toda peticion y la politica de datos
   * promete diez para una consulta, y las dos frases apuntan al mismo correo.
   */
  @Test
  void unaConsultaDeDatosVenceAntesQueUnaPeticion() throws Exception {
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/atencion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoRadicar("CONSULTA_DATOS")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.limiteDeRespuesta").value("2026-09-25T05:00:00Z"));
  }

  @Test
  void laBandejaTraeLoAbiertoYLoRespondidoSaleDeElla() throws Exception {
    autenticarComoAdmin();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/admin/atencion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRadicar("QUEJA")))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    mockMvc.perform(get("/api/v1/admin/atencion")).andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            post("/api/v1/admin/atencion/{id}/respuesta", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumen\":\"se reenvio la guia\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RESPONDIDA"))
        .andExpect(jsonPath("$.respuesta.resumen").value("se reenvio la guia"));

    mockMvc.perform(get("/api/v1/admin/atencion")).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void unTipoSinProrrogaNoSePuedeProrrogar() throws Exception {
    autenticarComoAdmin();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/admin/atencion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRadicar("PETICION")))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    mockMvc
        .perform(
            post("/api/v1/admin/atencion/{id}/prorroga", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"hace falta mas tiempo\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void unaSolicitudInexistenteDa404() throws Exception {
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/atencion/{id}/respuesta", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumen\":\"lo que sea\"}"))
        .andExpect(status().isNotFound());
  }

  @TestConfiguration
  static class Configuracion {

    private static final PlazosDeAtencion PLAZOS =
        PlazosDeAtencion.de(
            new PlazosDeAtencion.PlazoHabil(10, 5),
            new PlazosDeAtencion.PlazoHabil(15, 8),
            new PlazosDeAtencion.PlazoHabil(15, 0));

    @Bean
    Reloj reloj() {
      return () -> AHORA;
    }

    @Bean
    CalendarioHabil calendarioHabil() {
      return CalendarioHabil.sinFestivosCargados();
    }

    @Bean
    EnviadorDeCorreo enviadorDeCorreo() {
      return (destinatario, asunto, cuerpo) -> {};
    }

    @Bean
    RepositorioSolicitudesAtencion repositorioSolicitudesAtencion() {
      return new RepositorioSolicitudesAtencionDobleDePrueba();
    }

    @Bean
    RadicarSolicitud radicarSolicitud(
        RepositorioSolicitudesAtencion repositorio, EnviadorDeCorreo correos, Reloj reloj) {
      return new RadicarSolicitud(repositorio, correos, reloj);
    }

    @Bean
    ResponderSolicitud responderSolicitud(RepositorioSolicitudesAtencion repositorio, Reloj reloj) {
      return new ResponderSolicitud(repositorio, reloj);
    }

    @Bean
    ProrrogarSolicitud prorrogarSolicitud(
        RepositorioSolicitudesAtencion repositorio,
        CalendarioHabil calendario,
        EnviadorDeCorreo correos,
        Reloj reloj) {
      return new ProrrogarSolicitud(repositorio, PLAZOS, calendario, correos, reloj);
    }

    @Bean
    ListarSolicitudesDeAtencion listarSolicitudesDeAtencion(
        RepositorioSolicitudesAtencion repositorio, CalendarioHabil calendario, Reloj reloj) {
      return new ListarSolicitudesDeAtencion(repositorio, PLAZOS, calendario, reloj);
    }

    @Bean
    MapeadorRespuestasAtencion mapeador() {
      return new MapeadorRespuestasAtencion();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }
}
