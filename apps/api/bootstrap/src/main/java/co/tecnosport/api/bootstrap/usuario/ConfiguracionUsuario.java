package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
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
import co.tecnosport.api.application.usuario.VerificadorDeTokens;
import co.tecnosport.api.application.usuario.VerificarCorreo;
import co.tecnosport.api.bootstrap.compartido.PropiedadesLimiteAuth;
import co.tecnosport.api.bootstrap.legal.PropiedadesLegal;
import co.tecnosport.api.infrastructure.usuario.CodificadorDeClavesBCrypt;
import co.tecnosport.api.infrastructure.usuario.GeneradorDeTokensJwt;
import co.tecnosport.api.infrastructure.usuario.VerificadorDeTokensJwt;
import co.tecnosport.api.presentation.usuario.FiltroAutenticacionJwt;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mismo patrón que {@code ConfiguracionPedido}/{@code ConfiguracionWompi}. */
@Configuration
@EnableConfigurationProperties({
  PropiedadesJwt.class,
  PropiedadesLegal.class,
  PropiedadesAdminSemilla.class,
  PropiedadesVerificacionCorreo.class,
  PropiedadesRecuperacionClave.class
})
public class ConfiguracionUsuario {

  @Bean
  public CodificadorDeClaves codificadorDeClaves() {
    return new CodificadorDeClavesBCrypt();
  }

  @Bean
  public GeneradorDeTokens generadorDeTokens(PropiedadesJwt propiedades) {
    return new GeneradorDeTokensJwt(
        propiedades.secreto(), Duration.ofMinutes(propiedades.minutosAcceso()));
  }

  @Bean
  public VerificadorDeTokens verificadorDeTokens(PropiedadesJwt propiedades) {
    return new VerificadorDeTokensJwt(propiedades.secreto());
  }

  @Bean
  public FiltroAutenticacionJwt filtroAutenticacionJwt(VerificadorDeTokens verificadorDeTokens) {
    return new FiltroAutenticacionJwt(verificadorDeTokens);
  }

  @Bean
  public IniciarSesion iniciarSesion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      PropiedadesJwt propiedades,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite) {
    return new IniciarSesion(
        repositorioUsuarios,
        repositorioSesiones,
        codificadorDeClaves,
        generadorDeTokens,
        reloj,
        Duration.ofDays(propiedades.diasRefresco()),
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()));
  }

  @Bean
  public RefrescarToken refrescarToken(
      RepositorioSesiones repositorioSesiones,
      RepositorioUsuarios repositorioUsuarios,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      PropiedadesJwt propiedades) {
    return new RefrescarToken(
        repositorioSesiones,
        repositorioUsuarios,
        generadorDeTokens,
        reloj,
        Duration.ofDays(propiedades.diasRefresco()));
  }

  @Bean
  public CerrarSesion cerrarSesion(RepositorioSesiones repositorioSesiones, Reloj reloj) {
    return new CerrarSesion(repositorioSesiones, reloj);
  }

  @Bean
  public RegistrarUsuario registrarUsuario(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensVerificacion repositorioTokensVerificacion,
      CodificadorDeClaves codificadorDeClaves,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj,
      PropiedadesVerificacionCorreo propiedades,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite,
      RepositorioAutorizaciones repositorioAutorizaciones,
      PropiedadesLegal propiedadesLegal) {
    return new RegistrarUsuario(
        repositorioUsuarios,
        repositorioTokensVerificacion,
        codificadorDeClaves,
        enviadorDeCorreo,
        reloj,
        Duration.ofHours(propiedades.horasVencimiento()),
        propiedades.urlPublica() + "/es/cuenta/verificar-correo",
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()),
        repositorioAutorizaciones,
        propiedadesLegal.politicaDatosVersion());
  }

  @Bean
  public VerificarCorreo verificarCorreo(
      RepositorioTokensVerificacion repositorioTokensVerificacion,
      RepositorioUsuarios repositorioUsuarios,
      Reloj reloj) {
    return new VerificarCorreo(repositorioTokensVerificacion, repositorioUsuarios, reloj);
  }

  @Bean
  public SolicitarRecuperacion solicitarRecuperacion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensRecuperacion repositorioTokensRecuperacion,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj,
      PropiedadesRecuperacionClave propiedadesRecuperacion,
      PropiedadesVerificacionCorreo propiedadesVerificacion,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite) {
    return new SolicitarRecuperacion(
        repositorioUsuarios,
        repositorioTokensRecuperacion,
        enviadorDeCorreo,
        reloj,
        Duration.ofMinutes(propiedadesRecuperacion.minutosVencimiento()),
        propiedadesVerificacion.urlPublica() + "/es/cuenta/restablecer-clave",
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()));
  }

  @Bean
  public ConfirmarRecuperacion confirmarRecuperacion(
      RepositorioTokensRecuperacion repositorioTokensRecuperacion,
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      Reloj reloj) {
    return new ConfirmarRecuperacion(
        repositorioTokensRecuperacion,
        repositorioUsuarios,
        repositorioSesiones,
        codificadorDeClaves,
        reloj);
  }
}
