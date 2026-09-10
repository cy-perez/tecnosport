package co.tecnosport.api.bootstrap.atencion;

import co.tecnosport.api.application.atencion.ListarSolicitudesDeAtencion;
import co.tecnosport.api.application.atencion.ProrrogarSolicitud;
import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.atencion.PlazosDeAtencion;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El {@code CalendarioHabil} lo provee {@code ConfiguracionRetracto}: es el mismo para los dos
 * plazos y tenerlo dos veces sería tener dos calendarios que algún día divergen. Con eso, del
 * {@code TODO: FESTIVOS_COLOMBIA} cuelgan ya dos obligaciones legales y no una — cargarlo cierra
 * las dos a la vez.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesAtencion.class)
public class ConfiguracionAtencion {

  @Bean
  public PlazosDeAtencion plazosDeAtencion(PropiedadesAtencion propiedades) {
    return PlazosDeAtencion.de(
        new PlazosDeAtencion.PlazoHabil(
            propiedades.diasConsultaDatos(), propiedades.diasProrrogaConsultaDatos()),
        new PlazosDeAtencion.PlazoHabil(
            propiedades.diasReclamoDatos(), propiedades.diasProrrogaReclamoDatos()),
        new PlazosDeAtencion.PlazoHabil(propiedades.diasConsumidor(), 0));
  }

  @Bean
  public RadicarSolicitud radicarSolicitud(
      RepositorioSolicitudesAtencion repositorio, EnviadorDeCorreo enviadorDeCorreo, Reloj reloj) {
    return new RadicarSolicitud(repositorio, enviadorDeCorreo, reloj);
  }

  @Bean
  public ResponderSolicitud responderSolicitud(
      RepositorioSolicitudesAtencion repositorio, Reloj reloj) {
    return new ResponderSolicitud(repositorio, reloj);
  }

  @Bean
  public ProrrogarSolicitud prorrogarSolicitud(
      RepositorioSolicitudesAtencion repositorio,
      PlazosDeAtencion plazos,
      CalendarioHabil calendario,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj) {
    return new ProrrogarSolicitud(repositorio, plazos, calendario, enviadorDeCorreo, reloj);
  }

  @Bean
  public ListarSolicitudesDeAtencion listarSolicitudesDeAtencion(
      RepositorioSolicitudesAtencion repositorio,
      PlazosDeAtencion plazos,
      CalendarioHabil calendario,
      Reloj reloj) {
    return new ListarSolicitudesDeAtencion(repositorio, plazos, calendario, reloj);
  }
}
