package co.tecnosport.api.bootstrap.usuario;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.usuario.CerrarSesion;
import co.tecnosport.api.application.usuario.CodificadorDeClaves;
import co.tecnosport.api.application.usuario.GeneradorDeTokens;
import co.tecnosport.api.application.usuario.IniciarSesion;
import co.tecnosport.api.application.usuario.RefrescarToken;
import co.tecnosport.api.application.usuario.RepositorioSesiones;
import co.tecnosport.api.application.usuario.RepositorioUsuarios;
import co.tecnosport.api.application.usuario.VerificadorDeTokens;
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
@EnableConfigurationProperties({PropiedadesJwt.class, PropiedadesAdminSemilla.class})
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
      PropiedadesJwt propiedades) {
    return new IniciarSesion(
        repositorioUsuarios,
        repositorioSesiones,
        codificadorDeClaves,
        generadorDeTokens,
        reloj,
        Duration.ofDays(propiedades.diasRefresco()));
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
}
