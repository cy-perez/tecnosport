package co.tecnosport.api.presentation.sugerencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.application.sugerencia.EnviarSugerencia;
import co.tecnosport.api.application.sugerencia.RepositorioSugerencias;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.sugerencia.Sugerencia;
import co.tecnosport.api.presentation.compartido.TextosDeCorreoDobleDePrueba;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code @DirtiesContext} por lo mismo que los demás: los dobles son beans singleton del contexto
 * de prueba y arrastrarían lo guardado entre métodos.
 */
@WebMvcTest(SugerenciasControlador.class)
@Import(SugerenciasControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SugerenciasControladorTest {

  private static final Instant AHORA = Instant.parse("2026-09-26T15:00:00Z");

  static final List<Sugerencia> GUARDADAS = new ArrayList<>();
  static final List<AutorizacionDatos> CONSTANCIAS = new ArrayList<>();

  @Autowired private MockMvc mockMvc;

  @Test
  void aceptaUnaSugerenciaAnonimaYResponde202() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sugerencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"Falta el color negro.\",\"autorizaDatos\":false}"))
        .andExpect(status().isAccepted());

    assertEquals(1, GUARDADAS.size());
    assertEquals("Falta el color negro.", GUARDADAS.getFirst().mensaje());
    assertEquals(Optional.empty(), GUARDADAS.getFirst().correo());
    assertTrue(CONSTANCIAS.isEmpty());
  }

  /**
   * <b>Ni la IP ni la versión de la política viajan en el cuerpo</b> (regla dura #7). Esto fija la
   * primera: la constancia guarda la que resolvió {@code IpDelCliente} de la petición, no la que el
   * cuerpo diga. En {@code MockMvc} esa IP es {@code 127.0.0.1}.
   */
  @Test
  void laIpDeLaConstanciaSaleDeLaPeticionYNoDelCuerpo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sugerencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"mensaje\":\"Gracias.\",\"correo\":\"ana@ejemplo.com\","
                        + "\"autorizaDatos\":true,\"direccionIp\":\"9.9.9.9\"}"))
        .andExpect(status().isAccepted());

    assertEquals(1, CONSTANCIAS.size());
    assertEquals("127.0.0.1", CONSTANCIAS.getFirst().direccionIp());
  }

  /**
   * Un mensaje vacío es una excepción de dominio, y el {@code @RestControllerAdvice} la traduce a
   * 422 con su código. Lo que esto fija de verdad es que la ruta pública <b>valida en el
   * servidor</b>: el formulario del navegador no protege nada, porque cualquiera puede mandar este
   * cuerpo a mano.
   */
  @Test
  void unMensajeVacioSeRechazaConCodigoYNoSeGuarda() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sugerencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"   \",\"autorizaDatos\":false}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EXCEPCION_DE_DOMINIO"));

    assertTrue(GUARDADAS.isEmpty());
  }

  /** Con correo y sin el sí: 422 con el código de la autorización, y nada guardado. */
  @Test
  void conCorreoYSinAutorizacionSeRechaza() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sugerencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"mensaje\":\"Hola.\",\"correo\":\"ana@ejemplo.com\","
                        + "\"autorizaDatos\":false}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("AUTORIZACION_REQUERIDA"));

    assertTrue(GUARDADAS.isEmpty());
    assertTrue(CONSTANCIAS.isEmpty());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    Reloj reloj() {
      return () -> AHORA;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    RepositorioSugerencias repositorioSugerencias() {
      GUARDADAS.clear();
      return GUARDADAS::add;
    }

    @Bean
    RepositorioAutorizaciones repositorioAutorizaciones() {
      CONSTANCIAS.clear();
      return new RepositorioAutorizaciones() {
        @Override
        public void guardar(AutorizacionDatos autorizacion) {
          CONSTANCIAS.add(autorizacion);
        }

        @Override
        public List<AutorizacionDatos> buscarPorCorreo(CorreoElectronico correo) {
          return List.copyOf(CONSTANCIAS);
        }
      };
    }

    @Bean
    EnviadorDeCorreo enviadorDeCorreo() {
      return (destinatario, asunto, cuerpo) -> {};
    }

    @Bean
    LimitadorDeIntentos limitadorDeIntentos() {
      return new LimitadorDeIntentos() {
        @Override
        public boolean permitir(String clave, int maximoIntentos, Duration ventana, Instant ahora) {
          return true;
        }

        @Override
        public void olvidar(String clave) {}
      };
    }

    @Bean
    EnviarSugerencia enviarSugerencia(
        RepositorioSugerencias repositorioSugerencias,
        RepositorioAutorizaciones repositorioAutorizaciones,
        EnviadorDeCorreo enviadorDeCorreo,
        Reloj reloj,
        LimitadorDeIntentos limitadorDeIntentos) {
      return new EnviarSugerencia(
          repositorioSugerencias,
          repositorioAutorizaciones,
          enviadorDeCorreo,
          new TextosDeCorreoDobleDePrueba(),
          reloj,
          limitadorDeIntentos,
          3,
          Duration.ofMinutes(60),
          "2026-09-14",
          new CorreoElectronico("contacto@tecnosport.co"));
    }
  }
}
