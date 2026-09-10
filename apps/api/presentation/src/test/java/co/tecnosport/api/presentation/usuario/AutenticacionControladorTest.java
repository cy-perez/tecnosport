package co.tecnosport.api.presentation.usuario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.application.usuario.CerrarSesion;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.ConfirmarRecuperacion;
import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.application.usuario.IniciarSesion;
import co.tecnosport.api.application.usuario.RefrescarToken;
import co.tecnosport.api.application.usuario.RegistrarUsuario;
import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.application.usuario.RepositorioTokensRecuperacion;
import co.tecnosport.api.application.usuario.RepositorioTokensVerificacion;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.application.usuario.SolicitarRecuperacion;
import co.tecnosport.api.application.usuario.VerificarCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SesionRefresco;
import co.tecnosport.api.domain.usuario.TokenRecuperacionClave;
import co.tecnosport.api.domain.usuario.TokenVerificacionCorreo;
import co.tecnosport.api.domain.usuario.Usuario;
import co.tecnosport.api.presentation.usuario.dto.ConfirmarRecuperacionRequest;
import co.tecnosport.api.presentation.usuario.dto.IniciarSesionRequest;
import co.tecnosport.api.presentation.usuario.dto.RegistrarUsuarioRequest;
import co.tecnosport.api.presentation.usuario.dto.SolicitarRecuperacionRequest;
import co.tecnosport.api.presentation.usuario.dto.VerificarCorreoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
  @Autowired private RepositorioTokensVerificacionDobleDePrueba tokensVerificacion;
  @Autowired private RepositorioTokensRecuperacionDobleDePrueba tokensRecuperacion;
  @Autowired private LimitadorDeIntentosDobleDePrueba limitadorDeIntentos;

  private final ObjectMapper json = new ObjectMapper();
  private static final Duration VIGENCIA = Duration.ofDays(30);
  private static final Duration VIGENCIA_TOKEN_VERIFICACION = Duration.ofHours(24);
  private static final Duration VIGENCIA_TOKEN_RECUPERACION = Duration.ofMinutes(30);
  private static final String VERSION_POLITICA = "2026-09-07";
  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(15);

  @BeforeEach
  void reiniciarLimitadorDeIntentos() {
    // Bean compartido por todo el contexto de @WebMvcTest: sin esto, denegarSiempre() de una
    // prueba contaminaría a las que corran después en la misma clase.
    limitadorDeIntentos.reiniciar();
  }

  private Usuario conUsuarioAdmin(String correo, String claveTextoPlano) {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico(correo), "hash:" + claveTextoPlano, Rol.ADMIN, Instant.now());
    usuario.verificarCorreo(Instant.now());
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

  // Sin cookie no hay sesión que refrescar, y eso no es un fallo de autenticación: el frontend
  // pregunta en cada arranque porque la cookie es HttpOnly y no puede saberlo de otro modo. Con
  // 401, la consola de todo visitante anónimo se llenaba de un error en cada visita.
  @Test
  void refrescarSinCookieDevuelve204() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/refresco"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  void refrescarConCookieVaciaDevuelve204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresco").cookie(new jakarta.servlet.http.Cookie("refresco", "")))
        .andExpect(status().isNoContent());
  }

  // Una cookie que sí llega pero no sirve es otra cosa: alguien mandó un valor que no es de este
  // sistema, y ahí 401 es la respuesta correcta.
  @Test
  void refrescarConCookieQueNoEsUnUuidDevuelve401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresco")
                .cookie(new jakarta.servlet.http.Cookie("refresco", "no-soy-un-uuid")))
        .andExpect(status().isUnauthorized());
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
  void registrarUnCorreoNuevoDevuelve201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new RegistrarUsuarioRequest(
                            "cliente@tecnosport.co", "clave-segura", true))))
        .andExpect(status().isCreated());
  }

  /**
   * La casilla del navegador es una comodidad; la regla es del servidor. Un cliente que mande el
   * JSON a mano sin autorizar no crea la cuenta (Ley 1581 de 2012).
   */
  @Test
  void registrarSinAutorizarElTratamientoDeDatosDevuelve422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new RegistrarUsuarioRequest(
                            "cliente@tecnosport.co", "clave-segura", false))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.codigo").value("AUTORIZACION_REQUERIDA"));
  }

  /**
   * Un cuerpo que omite el campo no cae en false: Jackson 3 rechaza el JSON entero. El resultado
   * sigue siendo el seguro —no se crea la cuenta— pero por una razón distinta de la que sugiere el
   * primitivo, y conviene que quede fijada por una prueba y no por una suposición.
   */
  @Test
  void registrarSinElCampoDeAutorizacionTampocoCrearLaCuenta() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"cliente@tecnosport.co\",\"clave\":\"clave-segura\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.codigo").value("HTTP_MESSAGE_NOT_READABLE"));
  }

  @Test
  void registrarConLimiteDeIntentosExcedidoDevuelve429() throws Exception {
    limitadorDeIntentos.denegarSiempre();

    mockMvc
        .perform(
            post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new RegistrarUsuarioRequest(
                            "cliente@tecnosport.co", "clave-segura", true))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_INTENTOS_EXCEDIDO"));
  }

  @Test
  void registrarUnCorreoYaExistenteDevuelve409() throws Exception {
    conUsuarioAdmin("admin@tecnosport.co", "clave-correcta");

    mockMvc
        .perform(
            post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new RegistrarUsuarioRequest("admin@tecnosport.co", "clave-segura", true))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CORREO_YA_REGISTRADO"));
  }

  @Test
  void iniciarSesionConCorreoSinVerificarDevuelve403() throws Exception {
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("cliente-sin-verificar@tecnosport.co"),
            "hash:clave-correcta",
            Rol.CLIENTE,
            Instant.now());
    usuarios.conUsuario(usuario);

    mockMvc
        .perform(
            post("/api/v1/auth/sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new IniciarSesionRequest(
                            "cliente-sin-verificar@tecnosport.co", "clave-correcta"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CORREO_SIN_VERIFICAR"));
  }

  @Test
  void verificarConTokenVigenteDevuelve204() throws Exception {
    // Correo distinto al de las otras pruebas: RepositorioUsuariosDobleDePrueba es un bean
    // compartido por todo el contexto de @WebMvcTest, así que su estado sobrevive entre métodos de
    // prueba de esta clase — dos pruebas usando el mismo correo se pisan según el orden en que
    // JUnit las ejecute.
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("cliente-verificar@tecnosport.co"),
            "hash",
            Rol.CLIENTE,
            Instant.now());
    usuarios.conUsuario(usuario);
    TokenVerificacionCorreo token =
        TokenVerificacionCorreo.crear(usuario.id(), Instant.now(), VIGENCIA_TOKEN_VERIFICACION);
    tokensVerificacion.conToken(token);

    mockMvc
        .perform(
            post("/api/v1/auth/verificacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new VerificarCorreoRequest(token.id().toString()))))
        .andExpect(status().isNoContent());
  }

  @Test
  void verificarConTokenInexistenteDevuelve422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/verificacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new VerificarCorreoRequest(UUID.randomUUID().toString()))))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TOKEN_VERIFICACION_CORREO_INVALIDO"));
  }

  @Test
  void recuperacionConCorreoExistenteDevuelve204() throws Exception {
    conUsuarioAdmin("admin-recuperacion@tecnosport.co", "clave-correcta");

    mockMvc
        .perform(
            post("/api/v1/auth/recuperacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new SolicitarRecuperacionRequest("admin-recuperacion@tecnosport.co"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void recuperacionConCorreoInexistenteTambienDevuelve204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/recuperacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new SolicitarRecuperacionRequest("no-existe@tecnosport.co"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void confirmarRecuperacionConTokenVigenteDevuelve204() throws Exception {
    // Correo distinto al de las otras pruebas: RepositorioUsuariosDobleDePrueba es un bean
    // compartido por todo el contexto de @WebMvcTest, mismo motivo que en las pruebas de
    // verificación de correo.
    Usuario usuario =
        Usuario.crear(
            new CorreoElectronico("cliente-recuperar@tecnosport.co"),
            "hash",
            Rol.CLIENTE,
            Instant.now());
    usuarios.conUsuario(usuario);
    TokenRecuperacionClave token =
        TokenRecuperacionClave.crear(usuario.id(), Instant.now(), VIGENCIA_TOKEN_RECUPERACION);
    tokensRecuperacion.conToken(token);

    mockMvc
        .perform(
            post("/api/v1/auth/recuperacion/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new ConfirmarRecuperacionRequest(token.id().toString(), "clave-nueva"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void confirmarRecuperacionConTokenInexistenteDevuelve422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/recuperacion/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new ConfirmarRecuperacionRequest(
                            UUID.randomUUID().toString(), "clave-nueva"))))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TOKEN_RECUPERACION_CLAVE_INVALIDO"));
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
    RepositorioTokensVerificacionDobleDePrueba repositorioTokensVerificacion() {
      return new RepositorioTokensVerificacionDobleDePrueba();
    }

    @Bean
    RepositorioTokensRecuperacionDobleDePrueba repositorioTokensRecuperacion() {
      return new RepositorioTokensRecuperacionDobleDePrueba();
    }

    @Bean
    EnviadorDeCorreo enviadorDeCorreo() {
      return new EnviadorDeCorreoDobleDePrueba();
    }

    @Bean
    LimitadorDeIntentosDobleDePrueba limitadorDeIntentos() {
      return new LimitadorDeIntentosDobleDePrueba();
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
    RegistrarUsuario registrarUsuario(
        RepositorioUsuarios repositorioUsuarios,
        RepositorioTokensVerificacion repositorioTokensVerificacion,
        CodificadorDeClaves codificadorDeClaves,
        EnviadorDeCorreo enviadorDeCorreo,
        LimitadorDeIntentos limitadorDeIntentos,
        RepositorioAutorizaciones repositorioAutorizaciones) {
      return new RegistrarUsuario(
          repositorioUsuarios,
          repositorioTokensVerificacion,
          codificadorDeClaves,
          enviadorDeCorreo,
          (texto, argumentos) -> texto.clave(),
          Instant::now,
          VIGENCIA_TOKEN_VERIFICACION,
          "http://localhost:4200/es/cuenta/verificar-correo",
          limitadorDeIntentos,
          MAXIMO_INTENTOS_POR_CUENTA,
          VENTANA_INTENTOS_POR_CUENTA,
          repositorioAutorizaciones,
          VERSION_POLITICA);
    }

    @Bean
    RepositorioAutorizaciones repositorioAutorizaciones() {
      return new RepositorioAutorizacionesDobleDePrueba();
    }

    @Bean
    VerificarCorreo verificarCorreo(
        RepositorioTokensVerificacion repositorioTokensVerificacion,
        RepositorioUsuarios repositorioUsuarios) {
      return new VerificarCorreo(repositorioTokensVerificacion, repositorioUsuarios, Instant::now);
    }

    @Bean
    SolicitarRecuperacion solicitarRecuperacion(
        RepositorioUsuarios repositorioUsuarios,
        RepositorioTokensRecuperacion repositorioTokensRecuperacion,
        EnviadorDeCorreo enviadorDeCorreo,
        LimitadorDeIntentos limitadorDeIntentos) {
      return new SolicitarRecuperacion(
          repositorioUsuarios,
          repositorioTokensRecuperacion,
          enviadorDeCorreo,
          (texto, argumentos) -> texto.clave(),
          Instant::now,
          VIGENCIA_TOKEN_RECUPERACION,
          "http://localhost:4200/es/cuenta/restablecer-clave",
          limitadorDeIntentos,
          MAXIMO_INTENTOS_POR_CUENTA,
          VENTANA_INTENTOS_POR_CUENTA);
    }

    @Bean
    ConfirmarRecuperacion confirmarRecuperacion(
        RepositorioTokensRecuperacion repositorioTokensRecuperacion,
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSesiones repositorioSesiones,
        CodificadorDeClaves codificadorDeClaves) {
      return new ConfirmarRecuperacion(
          repositorioTokensRecuperacion,
          repositorioUsuarios,
          repositorioSesiones,
          codificadorDeClaves,
          Instant::now);
    }

    @Bean
    IniciarSesion iniciarSesion(
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSesiones repositorioSesiones,
        CodificadorDeClaves codificadorDeClaves,
        GeneradorDeTokens generadorDeTokens,
        LimitadorDeIntentos limitadorDeIntentos) {
      return new IniciarSesion(
          repositorioUsuarios,
          repositorioSesiones,
          codificadorDeClaves,
          generadorDeTokens,
          Instant::now,
          VIGENCIA,
          limitadorDeIntentos,
          MAXIMO_INTENTOS_POR_CUENTA,
          VENTANA_INTENTOS_POR_CUENTA);
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
