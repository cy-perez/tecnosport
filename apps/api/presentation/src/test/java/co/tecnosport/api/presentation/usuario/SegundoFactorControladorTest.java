package co.tecnosport.api.presentation.usuario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.RepositorioSegundosFactores;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.usuario.Rol;
import co.tecnosport.api.domain.usuario.SegundoFactor;
import co.tecnosport.api.domain.usuario.Totp;
import co.tecnosport.api.domain.usuario.Usuario;
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

  private Usuario admin;

  @BeforeEach
  void preparar() {
    usuarios.limpiar();
    factores.limpiar();
    limitador.reiniciar();
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
