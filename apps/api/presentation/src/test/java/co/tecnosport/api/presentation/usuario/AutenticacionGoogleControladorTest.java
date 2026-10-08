package co.tecnosport.api.presentation.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.usuario.CredencialGoogleInvalidaException;
import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.application.usuario.IdentidadGoogle;
import co.tecnosport.api.application.usuario.IniciarSesionConGoogle;
import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.application.usuario.VerificadorDeCredencialGoogle;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/** ADR-0074: la puerta de Google responde lo mismo que la de la clave, cookie incluida. */
@WebMvcTest(AutenticacionGoogleControlador.class)
@Import(AutenticacionGoogleControladorTest.Configuracion.class)
class AutenticacionGoogleControladorTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void laConfiguracionDiceElClienteYDeDondeSaleElScript() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/google/configuracion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.habilitado").value(true))
        .andExpect(jsonPath("$.clienteId").value("cliente.apps.googleusercontent.com"))
        .andExpect(jsonPath("$.urlScript").value("https://script.de.prueba/gsi"));
  }

  /** La sesión es la nuestra: el mismo token de acceso y la misma cookie de refresco. */
  @Test
  void unaCuentaNuevaConAutorizacionAbreSesionConLaCookieDeRefresco() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credencial\":\"valida\",\"autorizaDatos\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rol").value("CLIENTE"))
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(cookie().exists("refresco"))
        .andExpect(cookie().httpOnly("refresco", true))
        .andExpect(cookie().path("refresco", "/api/v1/auth"));
  }

  /** Desde «Iniciar sesión», sin cuenta: 409 y el sitio lo manda a «Crear cuenta». */
  @Test
  void sinCuentaYSinAutorizacionEs409ConSuCodigo() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credencial\":\"otra-valida\",\"autorizaDatos\":false}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CUENTA_GOOGLE_SIN_REGISTRO"));
  }

  @Test
  void unaCredencialQueNoPasaEs401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credencial\":\"falsificada\",\"autorizaDatos\":true}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIAL_GOOGLE_INVALIDA"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioUsuariosDobleDePrueba repositorioUsuarios() {
      return new RepositorioUsuariosDobleDePrueba();
    }

    @Bean
    RepositorioSesionesDobleDePrueba repositorioSesiones() {
      return new RepositorioSesionesDobleDePrueba();
    }

    @Bean
    GeneradorDeTokens generadorDeTokens() {
      return new GeneradorDeTokensDobleDePrueba();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    RepositorioAutorizacionesDobleDePrueba repositorioAutorizaciones() {
      return new RepositorioAutorizacionesDobleDePrueba();
    }

    @Bean
    LimitadorDeIntentosDobleDePrueba limitadorDeIntentos() {
      return new LimitadorDeIntentosDobleDePrueba();
    }

    /** Dos credenciales válidas, con cuentas distintas; cualquier otra no pasa. */
    @Bean
    VerificadorDeCredencialGoogle verificadorDeCredencialGoogle() {
      return credencial ->
          switch (credencial) {
            case "valida" -> new IdentidadGoogle("sub-1", "ana@gmail.com", true);
            case "otra-valida" -> new IdentidadGoogle("sub-2", "beto@gmail.com", true);
            default -> throw new CredencialGoogleInvalidaException();
          };
    }

    @Bean
    IniciarSesionConGoogle iniciarSesionConGoogle(
        VerificadorDeCredencialGoogle verificador,
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSesiones repositorioSesiones,
        GeneradorDeTokens generadorDeTokens,
        RepositorioAutorizacionesDobleDePrueba repositorioAutorizaciones,
        LimitadorDeIntentosDobleDePrueba limitadorDeIntentos) {
      return new IniciarSesionConGoogle(
          verificador,
          repositorioUsuarios,
          repositorioSesiones,
          generadorDeTokens,
          repositorioAutorizaciones,
          Instant::now,
          Duration.ofDays(30),
          limitadorDeIntentos,
          5,
          Duration.ofMinutes(15),
          "2026-10-08.2",
          "cliente.apps.googleusercontent.com");
    }

    @Bean
    AutenticacionGoogleControlador.ScriptDeGoogle scriptDeGoogle() {
      return new AutenticacionGoogleControlador.ScriptDeGoogle("https://script.de.prueba/gsi");
    }
  }
}
