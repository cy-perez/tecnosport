package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.application.usuario.CambiarClave;
import co.tecnosport.api.application.usuario.CerrarSesion;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.ConfirmarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.ConfirmarRecuperacion;
import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.application.usuario.IniciarActivacionSegundoFactor;
import co.tecnosport.api.application.usuario.IniciarSesion;
import co.tecnosport.api.application.usuario.IniciarSesionConGoogle;
import co.tecnosport.api.application.usuario.ReenviarVerificacion;
import co.tecnosport.api.application.usuario.RefrescarToken;
import co.tecnosport.api.application.usuario.RegistrarUsuario;
import co.tecnosport.api.application.usuario.RepositorioDesafiosSegundoFactor;
import co.tecnosport.api.application.usuario.RepositorioSegundosFactores;
import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.application.usuario.RepositorioTokensRecuperacion;
import co.tecnosport.api.application.usuario.RepositorioTokensVerificacion;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.application.usuario.SolicitarRecuperacion;
import co.tecnosport.api.application.usuario.VerificadorDeCredencialGoogle;
import co.tecnosport.api.application.usuario.VerificadorDeTokens;
import co.tecnosport.api.application.usuario.VerificarCorreo;
import co.tecnosport.api.application.usuario.VerificarSegundoFactor;
import co.tecnosport.api.bootstrap.compartido.PropiedadesApp;
import co.tecnosport.api.bootstrap.compartido.PropiedadesLimiteAuth;
import co.tecnosport.api.bootstrap.legal.PropiedadesLegal;
import co.tecnosport.api.infrastructure.usuario.CifradorAesGcm;
import co.tecnosport.api.infrastructure.usuario.CodificadorDeClavesBCrypt;
import co.tecnosport.api.infrastructure.usuario.GeneradorDeTokensJwt;
import co.tecnosport.api.infrastructure.usuario.VerificadorDeCredencialGoogleJwt;
import co.tecnosport.api.infrastructure.usuario.VerificadorDeTokensJwt;
import co.tecnosport.api.presentation.usuario.AutenticacionGoogleControlador;
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
  PropiedadesRecuperacionClave.class,
  PropiedadesGoogle.class,
  PropiedadesSegundoFactor.class
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
      PropiedadesLimiteAuth propiedadesLimite,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioDesafiosSegundoFactor repositorioDesafios,
      PropiedadesSegundoFactor propiedadesSegundoFactor) {
    return new IniciarSesion(
        repositorioUsuarios,
        repositorioSesiones,
        codificadorDeClaves,
        generadorDeTokens,
        reloj,
        Duration.ofDays(propiedades.diasRefresco()),
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        propiedadesLimite.cuentaGlobalMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()),
        repositorioSegundosFactores,
        repositorioDesafios,
        Duration.ofMinutes(propiedadesSegundoFactor.minutosDesafio()));
  }

  /** ADR-0096: los tres topes del segundo paso salen de {@link PropiedadesSegundoFactor}. */
  @Bean
  public VerificarSegundoFactor verificarSegundoFactor(
      RepositorioDesafiosSegundoFactor repositorioDesafios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      GeneradorDeTokens generadorDeTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      PropiedadesJwt propiedadesJwt,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesSegundoFactor propiedades) {
    return new VerificarSegundoFactor(
        repositorioDesafios,
        repositorioSegundosFactores,
        repositorioUsuarios,
        repositorioSesiones,
        generadorDeTokens,
        enviadorDeCorreo,
        textos,
        reloj,
        Duration.ofDays(propiedadesJwt.diasRefresco()),
        limitadorDeIntentos,
        propiedades.intentosPorDesafio(),
        propiedades.intentosCuentaIp(),
        propiedades.intentosCuenta(),
        Duration.ofMinutes(propiedades.minutosVentana()));
  }

  @Bean
  public VerificadorDeCredencialGoogle verificadorDeCredencialGoogle(
      PropiedadesGoogle propiedades) {
    return VerificadorDeCredencialGoogleJwt.contraLlavesDeGoogle(
        propiedades.urlLlaves(), propiedades.clienteId());
  }

  /** Los límites por IP del login: antes de verificar la credencial no se sabe de quién es. */
  @Bean
  public IniciarSesionConGoogle iniciarSesionConGoogle(
      VerificadorDeCredencialGoogle verificador,
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      GeneradorDeTokens generadorDeTokens,
      RepositorioAutorizaciones repositorioAutorizaciones,
      Reloj reloj,
      PropiedadesJwt propiedades,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite,
      PropiedadesLegal propiedadesLegal,
      PropiedadesGoogle propiedadesGoogle) {
    return new IniciarSesionConGoogle(
        verificador,
        repositorioUsuarios,
        repositorioSesiones,
        generadorDeTokens,
        repositorioAutorizaciones,
        reloj,
        Duration.ofDays(propiedades.diasRefresco()),
        limitadorDeIntentos,
        propiedadesLimite.ipMaximo(),
        Duration.ofMinutes(propiedadesLimite.ipMinutos()),
        propiedadesLegal.politicaDatosVersion(),
        propiedadesGoogle.clienteId());
  }

  @Bean
  public AutenticacionGoogleControlador.ScriptDeGoogle scriptDeGoogle(
      PropiedadesGoogle propiedades) {
    return new AutenticacionGoogleControlador.ScriptDeGoogle(propiedades.urlScript());
  }

  /**
   * Mismos limites que el login: el abuso que frena es el mismo -probar claves- y la ventana no
   * tiene por que ser otra.
   */
  @Bean
  public CambiarClave cambiarClave(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSesiones repositorioSesiones,
      CodificadorDeClaves codificadorDeClaves,
      GeneradorDeTokens generadorDeTokens,
      Reloj reloj,
      PropiedadesJwt propiedades,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite) {
    return new CambiarClave(
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
      TextosDeCorreo textos,
      Reloj reloj,
      PropiedadesVerificacionCorreo propiedades,
      PropiedadesApp propiedadesApp,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite,
      RepositorioAutorizaciones repositorioAutorizaciones,
      PropiedadesLegal propiedadesLegal) {
    return new RegistrarUsuario(
        repositorioUsuarios,
        repositorioTokensVerificacion,
        codificadorDeClaves,
        enviadorDeCorreo,
        textos,
        reloj,
        Duration.ofHours(propiedades.horasVencimiento()),
        propiedadesApp.urlPublica() + "/es/cuenta/verificar-correo",
        propiedadesApp.urlPublica() + "/es/cuenta/recuperar-clave",
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

  /**
   * Comparte con {@code registrarUsuario} la vigencia del token y la ruta del enlace: es el mismo
   * correo y el mismo destino, y tenerlos escritos dos veces sería la forma de que un día dejaran
   * de coincidir.
   */
  @Bean
  public ReenviarVerificacion reenviarVerificacion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensVerificacion repositorioTokensVerificacion,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      PropiedadesVerificacionCorreo propiedadesVerificacion,
      PropiedadesApp propiedadesApp,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite) {
    return new ReenviarVerificacion(
        repositorioUsuarios,
        repositorioTokensVerificacion,
        enviadorDeCorreo,
        textos,
        reloj,
        Duration.ofHours(propiedadesVerificacion.horasVencimiento()),
        propiedadesApp.urlPublica() + "/es/cuenta/verificar-correo",
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()));
  }

  @Bean
  public SolicitarRecuperacion solicitarRecuperacion(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioTokensRecuperacion repositorioTokensRecuperacion,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      PropiedadesRecuperacionClave propiedadesRecuperacion,
      PropiedadesApp propiedadesApp,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite) {
    return new SolicitarRecuperacion(
        repositorioUsuarios,
        repositorioTokensRecuperacion,
        enviadorDeCorreo,
        textos,
        reloj,
        Duration.ofMinutes(propiedadesRecuperacion.minutosVencimiento()),
        propiedadesApp.urlPublica() + "/es/cuenta/restablecer-clave",
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

  /**
   * La llave AES-256 de los segundos factores (ADR-0096). Lo construye este módulo y no un
   * componente de {@code infrastructure} porque la llave es configuración, y la configuración se
   * lee aquí.
   */
  @Bean
  public CifradorAesGcm cifradorAesGcm(PropiedadesSegundoFactor propiedades) {
    return new CifradorAesGcm(propiedades.llaveEnBytes());
  }

  /** Mismo límite que el cambio de clave: el abuso que frena es el mismo, probar claves. */
  @Bean
  public IniciarActivacionSegundoFactor iniciarActivacionSegundoFactor(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      CodificadorDeClaves codificadorDeClaves,
      Reloj reloj,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite,
      PropiedadesSegundoFactor propiedades) {
    return new IniciarActivacionSegundoFactor(
        repositorioUsuarios,
        repositorioSegundosFactores,
        codificadorDeClaves,
        reloj,
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()),
        propiedades.emisor());
  }

  /** El código son seis dígitos: el tope es el del cambio de clave, por cuenta. */
  @Bean
  public ConfirmarActivacionSegundoFactor confirmarActivacionSegundoFactor(
      RepositorioUsuarios repositorioUsuarios,
      RepositorioSegundosFactores repositorioSegundosFactores,
      RepositorioSesiones repositorioSesiones,
      GeneradorDeTokens generadorDeTokens,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      PropiedadesJwt propiedadesJwt,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteAuth propiedadesLimite,
      PropiedadesSegundoFactor propiedades) {
    return new ConfirmarActivacionSegundoFactor(
        repositorioUsuarios,
        repositorioSegundosFactores,
        repositorioSesiones,
        generadorDeTokens,
        enviadorDeCorreo,
        textos,
        reloj,
        Duration.ofDays(propiedadesJwt.diasRefresco()),
        Duration.ofMinutes(propiedades.minutosActivacionPendiente()),
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()));
  }
}
