package co.tecnosport.api.bootstrap.reversion;

import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reversion.RadicarReversion;
import co.tecnosport.api.application.reversion.RegistrarGestionReversion;
import co.tecnosport.api.application.reversion.RepositorioSolicitudesReversion;
import co.tecnosport.api.application.reversion.ResolverReversion;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El {@code CalendarioHabil} lo provee {@code ConfiguracionRetracto}: es el mismo para los tres
 * plazos hábiles del sistema —retracto, respuesta a una PQR y solicitud de reversión— y tenerlo
 * tres veces sería tener tres calendarios que algún día divergen.
 */
@Configuration
public class ConfiguracionReversion {

  @Bean
  public RadicarReversion radicarReversion(
      RepositorioSolicitudesReversion repositorioReversiones,
      RepositorioPedidos repositorioPedidos,
      RadicarSolicitud radicarSolicitud,
      CalendarioHabil calendario,
      Reloj reloj) {
    return new RadicarReversion(
        repositorioReversiones, repositorioPedidos, radicarSolicitud, calendario, reloj);
  }

  @Bean
  public RegistrarGestionReversion registrarGestionReversion(
      RepositorioSolicitudesReversion repositorio, Reloj reloj) {
    return new RegistrarGestionReversion(repositorio, reloj);
  }

  @Bean
  public ResolverReversion resolverReversion(
      RepositorioSolicitudesReversion repositorioReversiones,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      ResponderSolicitud responderSolicitud,
      Reloj reloj) {
    return new ResolverReversion(
        repositorioReversiones,
        repositorioPedidos,
        repositorioReintegros,
        responderSolicitud,
        reloj);
  }
}
