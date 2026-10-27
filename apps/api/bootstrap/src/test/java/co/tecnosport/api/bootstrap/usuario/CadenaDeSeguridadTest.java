package co.tecnosport.api.bootstrap.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.AlcanceDeAcceso;
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
    return tokenDe(rol, AlcanceDeAcceso.COMPLETO);
  }

  private String tokenDe(Rol rol, AlcanceDeAcceso alcance) {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("prueba-cadena@tecnosport.co"), "hash", rol, Instant.now());
    return generadorDeTokens.generarAcceso(usuario, alcance, Instant.now());
  }

  /**
   * ADR-0096. La sesión restringida de un administrador sin segundo factor —el panel se lo exige—
   * no abre el panel, y sí abre la activación. Con un 401 en el panel el frontend refrescaría en
   * bucle; con un 403 sabe que tiene que llevarla a activar el factor.
   */
  @Test
  void laSesionRestringidaSoloAbreElSegundoFactor() throws Exception {
    String restringido = tokenDe(Rol.ADMIN, AlcanceDeAcceso.SOLO_ACTIVAR_SEGUNDO_FACTOR);
    mockMvc
        .perform(get("/api/v1/admin/pedidos").header("Authorization", "Bearer " + restringido))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/auth/segundo-factor/activacion")
                .header("Authorization", "Bearer " + restringido)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveActual\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
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

  /**
   * ADR-0096. El segundo factor es del panel: sin sesión es un 401 —para que el frontend refresque—
   * y con la de un cliente, un 403. Con la de un administrador la cadena deja pasar y responde el
   * caso de uso: el token de la prueba no es de nadie que exista, así que la clave no coincide.
   */
  @Test
  void elSegundoFactorEsDelPanel() throws Exception {
    String cuerpo = "{\"claveActual\":\"x\"}";
    mockMvc
        .perform(
            post("/api/v1/auth/segundo-factor/activacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/auth/segundo-factor/activacion")
                .header("Authorization", "Bearer " + tokenDe(Rol.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/auth/segundo-factor/activacion")
                .header("Authorization", "Bearer " + tokenDe(Rol.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
  }

  /**
   * El segundo paso del inicio de sesión es público: quien llega todavía no tiene token. Si la
   * regla de arriba lo cerrara, el 401 sería {@code NO_AUTENTICADO} y nadie con segundo factor
   * podría entrar; abierto, responde el caso de uso.
   */
  @Test
  void elSegundoPasoDelInicioDeSesionEsPublico() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/segundo-factor/verificacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafio\":\"no-es-un-uuid\",\"codigo\":\"123456\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("DESAFIO_SEGUNDO_FACTOR_INVALIDO"));
  }

  /**
   * El endpoint de Cloud Tasks (ADR-0080) no lo abre una sesión del panel, ni siquiera la de un
   * administrador: solo el token de la cuenta de las tareas. Sin cola configurada —como aquí—, no
   * lo abre nada. Si esta regla se cayera, el {@code anyRequest().permitAll()} lo dejaría abierto a
   * cualquiera en una API pública.
   */
  @Test
  void elEndpointDeLaColaNoLoAbreNiUnaSesionDeAdministrador() throws Exception {
    String ruta = "/api/v1/internal/ingestas/" + java.util.UUID.randomUUID() + "/tramo";
    mockMvc.perform(post(ruta)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(ruta).header("Authorization", "Bearer " + tokenDe(Rol.ADMIN)))
        .andExpect(status().isForbidden());
  }
}
