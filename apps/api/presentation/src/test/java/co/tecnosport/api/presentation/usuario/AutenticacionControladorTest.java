package co.tecnosport.api.presentation.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.usuario.CerrarSesion;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.application.usuario.IniciarSesion;
import co.tecnosport.api.application.usuario.RefrescarToken;
import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.Usuario;
import co.tecnosport.api.presentation.usuario.dto.IniciarSesionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(AutenticacionControlador.class)
@Import(AutenticacionControladorTest.Configuracion.class)
class AutenticacionControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioUsuariosDobleDePrueba usuarios;
  @Autowired private RepositorioSesionesDobleDePrueba sesiones;

  private final ObjectMapper json = new ObjectMapper();
  private static final Duration VIGENCIA = Duration.ofDays(30);

  private Usuario conUsuarioAdmin(String correo, String claveTextoPlano) {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico(correo), "hash:" + claveTextoPlano, Rol.ADMIN, Instant.now());
    usuarios.conUsuario(usuario);
    return usuario;
  }

  @Test
  void iniciarSesionExitosoDevuelveTokensYCookie() throws Exception {
    conUsuarioAdmin("admin@tecnosport.co", "clave-correcta");

    mockMvc
        .perform(
            post("/api/v1/auth/sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new IniciarSesionRequest("admin@tecnosport.co", "clave-correcta"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rol").value("ADMIN"))
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(cookie().exists("refresco"))
        .andExpect(cookie().httpOnly("refresco", true));
  }

  @Test
  void iniciarSesionConCredencialesInvalidasDevuelve401() throws Exception {
    conUsuarioAdmin("admin@tecnosport.co", "clave-correcta");

    mockMvc
        .perform(
            post("/api/v1/auth/sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new IniciarSesionRequest("admin@tecnosport.co", "clave-incorrecta"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
  }

  @Test
  void refrescarConCookieValidaRotaLaCookie() throws Exception {
    Usuario usuario = conUsuarioAdmin("admin@tecnosport.co", "clave-correcta");
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), Instant.now(), VIGENCIA);
    sesiones.guardar(sesion);

    mockMvc
        .perform(
            post("/api/v1/auth/refresco")
                .cookie(new jakarta.servlet.http.Cookie("refresco", sesion.id().toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(cookie().exists("refresco"));
  }

  @Test
  void refrescarSinCookieDevuelve401() throws Exception {
    mockMvc.perform(post("/api/v1/auth/refresco")).andExpect(status().isUnauthorized());
  }

  @Test
  void refrescarUnaSesionYaUsadaDevuelve401() throws Exception {
    Usuario usuario = conUsuarioAdmin("admin@tecnosport.co", "clave-correcta");
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), Instant.now(), VIGENCIA);
    sesiones.guardar(sesion);
    mockMvc.perform(
        post("/api/v1/auth/refresco")
            .cookie(new jakarta.servlet.http.Cookie("refresco", sesion.id().toString())));

    mockMvc
        .perform(
            post("/api/v1/auth/refresco")
                .cookie(new jakarta.servlet.http.Cookie("refresco", sesion.id().toString())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("SESION_DE_REFRESCO_COMPROMETIDA"));
  }

  @Test
  void cerrarSesionLimpiaLaCookie() throws Exception {
    Usuario usuario = conUsuarioAdmin("admin@tecnosport.co", "clave-correcta");
    SesionRefresco sesion =
        SesionRefresco.crear(usuario.id(), UUID.randomUUID(), Instant.now(), VIGENCIA);
    sesiones.guardar(sesion);

    mockMvc
        .perform(
            post("/api/v1/auth/cierre")
                .cookie(new jakarta.servlet.http.Cookie("refresco", sesion.id().toString())))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refresco", 0));
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
    CodificadorDeClaves codificadorDeClaves() {
      return new CodificadorDeClavesDobleDePrueba();
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
    IniciarSesion iniciarSesion(
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSesiones repositorioSesiones,
        CodificadorDeClaves codificadorDeClaves,
        GeneradorDeTokens generadorDeTokens) {
      return new IniciarSesion(
          repositorioUsuarios,
          repositorioSesiones,
          codificadorDeClaves,
          generadorDeTokens,
          Instant::now,
          VIGENCIA);
    }

    @Bean
    RefrescarToken refrescarToken(
        RepositorioSesiones repositorioSesiones,
        RepositorioUsuarios repositorioUsuarios,
        GeneradorDeTokens generadorDeTokens) {
      return new RefrescarToken(
          repositorioSesiones, repositorioUsuarios, generadorDeTokens, Instant::now, VIGENCIA);
    }

    @Bean
    CerrarSesion cerrarSesion(RepositorioSesiones repositorioSesiones) {
      return new CerrarSesion(repositorioSesiones, Instant::now);
    }
  }
}
