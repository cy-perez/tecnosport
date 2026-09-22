package co.tecnosport.api.bootstrap.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.Usuario;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Los dos códigos que la cadena de seguridad tiene que distinguir, contra la cadena de verdad y no
 * contra un {@code @WebMvcTest} —que no monta {@code ConfiguracionSeguridad} porque vive en este
 * módulo—. Esa es justamente la razón de que el 403 pudiera quedarse ahí meses: ninguna prueba
 * miraba la cadena completa.
 *
 * <p>Perfiles como {@code ContratoOpenApiTest} y por lo mismo: bajo {@code e2e} los clientes de
 * terceros los sustituyen sus dobles y nadie le pide credenciales a una prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"local", "e2e"})
class CadenaDeSeguridadTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private MockMvc mockMvc;
  @Autowired private GeneradorDeTokens generadorDeTokens;

  private String tokenDe(Rol rol) {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("prueba-cadena@tecnosport.co"), "hash", rol, Instant.now());
    return generadorDeTokens.generarAcceso(usuario, Instant.now());
  }

  @Test
  void sinTokenElPanelResponde401YNo403() throws Exception {
    // 401 y no 403: el frontend renueva el token de acceso al recibir un 401
    // (`crearClienteAutenticado`), así que con 403 esa renovación no dispara nunca.
    mockMvc
        .perform(get("/api/v1/admin/pedidos"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
  }

  @Test
  void conUnTokenQueNoSirveElPanelResponde401() throws Exception {
    // Un token vencido toma este mismo camino: el verificador devuelve vacío y nadie queda
    // autenticado. Es el caso que de verdad vive una pantalla abierta más de quince minutos.
    mockMvc
        .perform(get("/api/v1/admin/pedidos").header("Authorization", "Bearer basura"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void conSesionDeClienteElPanelSigueRespondiendo403() throws Exception {
    // El 403 no desaparece, cambia de sitio: aquí sí se sabe quién es, y no puede.
    mockMvc
        .perform(
            get("/api/v1/admin/pedidos").header("Authorization", "Bearer " + tokenDe(Rol.CLIENTE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cambiarLaClaveSinSesionResponde401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/clave")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveActual\":\"a\",\"claveNueva\":\"b\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void elRestoDeAuthSigueSiendoPublico() throws Exception {
    // La guarda de que declarar el punto de entrada no cerró nada que estaba abierto: quien
    // inicia sesión todavía no tiene token, y un 401 aquí dejaría a todo el mundo fuera.
    mockMvc
        .perform(
            post("/api/v1/auth/sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"nadie@tecnosport.co\",\"clave\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
  }
}
