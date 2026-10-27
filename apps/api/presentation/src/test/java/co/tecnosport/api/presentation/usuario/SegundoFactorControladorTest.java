package co.tecnosport.api.presentation.usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.ConfirmarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.RepositorioDesafiosSegundoFactor;
import co.tecnosport.api.application.usuario.RepositorioSegundosFactores;
import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.application.usuario.VerificarSegundoFactor;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.DesafioSegundoFactor;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
import co.tecnosport.api.presentation.compartido.TextosDeCorreoDobleDePrueba;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(SegundoFactorControlador.class)
@Import(SegundoFactorControladorTest.Configuracion.class)
class SegundoFactorControladorTest {

  private static final Duration VENTANA = Duration.ofMinutes(15);

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioUsuariosDobleDePrueba usuarios;
  @Autowired private RepositorioSegundosFactoresDobleDePrueba factores;
  @Autowired private LimitadorDeIntentosDobleDePrueba limitador;
  @Autowired private RepositorioSesionesDobleDePrueba sesiones;
  @Autowired private RepositorioDesafiosSegundoFactorDobleDePrueba desafios;

  private Usuario admin;

  @BeforeEach
  void preparar() {
    usuarios.limpiar();
    factores.limpiar();
    limitador.reiniciar();
    sesiones.limpiar();
    desafios.limpiar();
    admin =
        Usuario.crear(
            new CorreoElectronico("admin@tecnosport.co"), "hash:clave", Rol.ADMIN, Instant.now());
    admin.verificarCorreo(Instant.now());
    usuarios.conUsuario(admin);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(admin.id(), null));
  }

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  private org.springframework.test.web.servlet.ResultActions activar(String clave)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/segundo-factor/activacion")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"claveActual\":\"" + clave + "\"}"));
  }

  @Test
  void iniciarLaActivacionDevuelveLaClaveYLaUri() throws Exception {
    activar("clave")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secreto").isString())
        .andExpect(jsonPath("$.uri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
        .andExpect(jsonPath("$.digitos").value(6))
        .andExpect(jsonPath("$.periodoSegundos").value(30));
    assertFalse(factores.de(admin.id()).orElseThrow().activo());
  }

  @Test
  void conLaClaveEquivocadaResponde401() throws Exception {
    activar("otra")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
  }

  @Test
  void conElFactorYaActivoResponde409() throws Exception {
    SegundoFactor activo = SegundoFactor.iniciar(admin.id(), Instant.now());
    activo.confirmar(
        Totp.codigoEn(activo.clave(), Totp.pasoDe(Instant.now())), Instant.now(), VENTANA);
    factores.con(activo);

    activar("clave")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("SEGUNDO_FACTOR_YA_ACTIVO"));
  }

  @Test
  void conElLimiteAgotadoResponde429() throws Exception {
    limitador.denegarSiempre();
    activar("clave")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_INTENTOS_EXCEDIDO"));
  }

  @Test
  void sinClaveEnElCuerpoResponde422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/segundo-factor/activacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  private org.springframework.test.web.servlet.ResultActions confirmar(String codigo)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/segundo-factor/activacion/confirmar")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\":\"" + codigo + "\"}"));
  }

  private SegundoFactor pendiente() {
    SegundoFactor factor = SegundoFactor.iniciar(admin.id(), Instant.now());
    factores.con(factor);
    return factor;
  }

  @Test
  void confirmarConElCodigoActivaYAbreSesionConCookieYCodigos() throws Exception {
    SegundoFactor factor = pendiente();

    confirmar(Totp.codigoEn(factor.clave(), Totp.pasoDe(Instant.now())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sesion.accessToken").isString())
        .andExpect(jsonPath("$.sesion.rol").value("ADMIN"))
        .andExpect(jsonPath("$.codigosDeRespaldo.length()").value(10))
        .andExpect(cookie().exists("refresco"))
        .andExpect(cookie().httpOnly("refresco", true));
    assertTrue(factores.de(admin.id()).orElseThrow().activo());
  }

  @Test
  void confirmarConUnCodigoEquivocadoResponde401() throws Exception {
    SegundoFactor factor = pendiente();

    confirmar(Totp.codigoEn(factor.clave(), Totp.pasoDe(Instant.now()) + 10))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CODIGO_SEGUNDO_FACTOR_INVALIDO"))
        .andExpect(cookie().doesNotExist("refresco"));
    assertFalse(factores.de(admin.id()).orElseThrow().activo());
  }

  @Test
  void confirmarSinActivacionIniciadaResponde409() throws Exception {
    confirmar("123456")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ACTIVACION_SEGUNDO_FACTOR_NO_INICIADA"));
  }

  @Test
  void confirmarUnFactorYaActivoResponde409() throws Exception {
    SegundoFactor activo = SegundoFactor.iniciar(admin.id(), Instant.now());
    activo.confirmar(
        Totp.codigoEn(activo.clave(), Totp.pasoDe(Instant.now())), Instant.now(), VENTANA);
    factores.con(activo);

    confirmar("123456")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("SEGUNDO_FACTOR_YA_ACTIVO"));
  }

  private org.springframework.test.web.servlet.ResultActions verificar(
      String desafio, String codigo) throws Exception {
    // Sin sesión: quien llega al segundo paso todavía no la tiene.
    SecurityContextHolder.clearContext();
    return mockMvc.perform(
        post("/api/v1/auth/segundo-factor/verificacion")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"));
  }

  private SegundoFactor activoDesdeHaceUnaHora() {
    Instant antes = Instant.now().minus(Duration.ofHours(1));
    SegundoFactor factor = SegundoFactor.iniciar(admin.id(), antes);
    factor.confirmar(Totp.codigoEn(factor.clave(), Totp.pasoDe(antes)), antes, VENTANA);
    factores.con(factor);
    return factor;
  }

  private DesafioSegundoFactor desafio() {
    DesafioSegundoFactor desafio =
        DesafioSegundoFactor.crear(admin.id(), Instant.now(), Duration.ofMinutes(5));
    desafios.guardar(desafio);
    return desafio;
  }

  @Test
  void verificarConElCodigoAbreLaSesionConCookie() throws Exception {
    SegundoFactor factor = activoDesdeHaceUnaHora();

    verificar(desafio().id().toString(), Totp.codigoEn(factor.clave(), Totp.pasoDe(Instant.now())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.rol").value("ADMIN"))
        .andExpect(cookie().exists("refresco"))
        .andExpect(cookie().httpOnly("refresco", true));
  }

  @Test
  void verificarConUnCodigoEquivocadoResponde401ConSuCodigo() throws Exception {
    SegundoFactor factor = activoDesdeHaceUnaHora();

    verificar(
            desafio().id().toString(),
            Totp.codigoEn(factor.clave(), Totp.pasoDe(Instant.now()) + 10))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CODIGO_SEGUNDO_FACTOR_INVALIDO"))
        .andExpect(cookie().doesNotExist("refresco"));
  }

  @Test
  void verificarConUnDesafioQueNoSirveResponde401ConSuCodigo() throws Exception {
    activoDesdeHaceUnaHora();

    verificar("no-es-un-uuid", "123456")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("DESAFIO_SEGUNDO_FACTOR_INVALIDO"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioUsuariosDobleDePrueba repositorioUsuarios() {
      return new RepositorioUsuariosDobleDePrueba();
    }

    @Bean
    RepositorioSegundosFactoresDobleDePrueba repositorioSegundosFactores() {
      return new RepositorioSegundosFactoresDobleDePrueba();
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
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
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
    EnviadorDeCorreo enviadorDeCorreo() {
      return new EnviadorDeCorreoDobleDePrueba();
    }

    @Bean
    ConfirmarActivacionSegundoFactor confirmarActivacionSegundoFactor(
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSegundosFactores repositorioSegundosFactores,
        RepositorioSesiones repositorioSesiones,
        GeneradorDeTokens generadorDeTokens,
        EnviadorDeCorreo enviadorDeCorreo,
        LimitadorDeIntentos limitadorDeIntentos) {
      return new ConfirmarActivacionSegundoFactor(
          repositorioUsuarios,
          repositorioSegundosFactores,
          repositorioSesiones,
          generadorDeTokens,
          enviadorDeCorreo,
          new TextosDeCorreoDobleDePrueba(),
          Instant::now,
          Duration.ofDays(30),
          VENTANA,
          limitadorDeIntentos,
          5,
          VENTANA);
    }

    @Bean
    RepositorioDesafiosSegundoFactorDobleDePrueba repositorioDesafios() {
      return new RepositorioDesafiosSegundoFactorDobleDePrueba();
    }

    @Bean
    VerificarSegundoFactor verificarSegundoFactor(
        RepositorioDesafiosSegundoFactor repositorioDesafios,
        RepositorioSegundosFactores repositorioSegundosFactores,
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSesiones repositorioSesiones,
        GeneradorDeTokens generadorDeTokens,
        EnviadorDeCorreo enviadorDeCorreo,
        LimitadorDeIntentos limitadorDeIntentos) {
      return new VerificarSegundoFactor(
          repositorioDesafios,
          repositorioSegundosFactores,
          repositorioUsuarios,
          repositorioSesiones,
          generadorDeTokens,
          enviadorDeCorreo,
          new TextosDeCorreoDobleDePrueba(),
          Instant::now,
          Duration.ofDays(30),
          limitadorDeIntentos,
          5,
          5,
          10,
          VENTANA);
    }

    @Bean
    IniciarActivacionSegundoFactor iniciarActivacionSegundoFactor(
        RepositorioUsuarios repositorioUsuarios,
        RepositorioSegundosFactores repositorioSegundosFactores,
        CodificadorDeClaves codificadorDeClaves,
        LimitadorDeIntentos limitadorDeIntentos) {
      return new IniciarActivacionSegundoFactor(
          repositorioUsuarios,
          repositorioSegundosFactores,
          codificadorDeClaves,
          Instant::now,
          limitadorDeIntentos,
          5,
          VENTANA,
          "TecnoSport");
    }
  }
}
