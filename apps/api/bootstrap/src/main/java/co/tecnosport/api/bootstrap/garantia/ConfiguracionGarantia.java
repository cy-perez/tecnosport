package co.tecnosport.api.bootstrap.garantia;

import co.tecnosport.api.application.atencion.RadicarSolicitud;
import co.tecnosport.api.application.atencion.RepositorioSolicitudesAtencion;
import co.tecnosport.api.application.atencion.ResponderSolicitud;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.garantia.RadicarReclamacionGarantia;
import co.tecnosport.api.application.garantia.RepositorioReclamacionesGarantia;
import co.tecnosport.api.application.garantia.ResolverGarantia;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.garantia.TerminosDeGarantia;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PropiedadesGarantia.class)
public class ConfiguracionGarantia {

  @Bean
  public TerminosDeGarantia terminosDeGarantia(PropiedadesGarantia propiedades) {
    return TerminosDeGarantia.de(
        propiedades.mesesPorDefecto(),
        propiedades.mesesPorCategoria(),
        Set.copyOf(propiedades.categoriasSinTerminoConocido()));
  }

  @Bean
  public RadicarReclamacionGarantia radicarReclamacionGarantia(
      RepositorioReclamacionesGarantia repositorioReclamaciones,
      RepositorioPedidos repositorioPedidos,
      RepositorioProductos repositorioProductos,
      RadicarSolicitud radicarSolicitud,
      TerminosDeGarantia terminos,
      Reloj reloj) {
    return new RadicarReclamacionGarantia(
        repositorioReclamaciones,
        repositorioPedidos,
        repositorioProductos,
        radicarSolicitud,
        terminos,
        reloj);
  }

  @Bean
  public ResolverGarantia resolverGarantia(
      RepositorioReclamacionesGarantia repositorioReclamaciones,
      RepositorioSolicitudesAtencion repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      ResponderSolicitud responderSolicitud,
      Reloj reloj) {
    return new ResolverGarantia(
        repositorioReclamaciones,
        repositorioSolicitudes,
        repositorioPedidos,
        repositorioReintegros,
        responderSolicitud,
        reloj);
  }
}
