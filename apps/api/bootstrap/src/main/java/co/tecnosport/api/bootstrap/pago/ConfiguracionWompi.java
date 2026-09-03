package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.application.pago.ProcesarEventoDePago;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.infrastructure.pago.WompiClient;
import co.tecnosport.api.presentation.pago.PropiedadesWompiPublicas;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mismo patrón que {@code ConfiguracionPedido}. Activa las dos propiedades de Wompi: {@code
 * PropiedadesWompi} (bootstrap, los secretos) y {@code PropiedadesWompiPublicas} (presentation, lo
 * que puede llegar al cliente) — bootstrap puede depender de presentation, nunca al revés.
 */
@Configuration
@EnableConfigurationProperties({PropiedadesWompi.class, PropiedadesWompiPublicas.class})
public class ConfiguracionWompi {

  @Bean
  public PasarelaDePagos pasarelaDePagos(PropiedadesWompi propiedades) {
    return new WompiClient(propiedades.secretoIntegridad(), propiedades.secretoEventos());
  }

  @Bean
  public CrearIntentoDePago crearIntentoDePago(
      RepositorioPedidos repositorioPedidos,
      RepositorioPagos repositorioPagos,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj) {
    return new CrearIntentoDePago(repositorioPedidos, repositorioPagos, pasarelaDePagos, reloj);
  }

  @Bean
  public ProcesarEventoDePago procesarEventoDePago(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj) {
    return new ProcesarEventoDePago(repositorioPagos, repositorioPedidos, pasarelaDePagos, reloj);
  }
}
