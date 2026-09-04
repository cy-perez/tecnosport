package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pago.ConciliarPagosPendientes;
import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.application.pago.ProcesarEventoDePago;
import co.tecnosport.api.application.pago.RegistrarIdTransaccionWompi;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.infrastructure.pago.WompiClient;
import co.tecnosport.api.presentation.pago.PropiedadesWompiPublicas;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mismo patrón que {@code ConfiguracionPedido}. Activa las tres propiedades de Wompi: {@code
 * PropiedadesWompi} (bootstrap, los secretos), {@code PropiedadesWompiPublicas} (presentation, lo
 * que puede llegar al cliente) y {@code PropiedadesConciliacionWompi} — bootstrap puede depender de
 * presentation, nunca al revés.
 */
@Configuration
@EnableConfigurationProperties({
  PropiedadesWompi.class,
  PropiedadesWompiPublicas.class,
  PropiedadesConciliacionWompi.class
})
public class ConfiguracionWompi {

  @Bean
  public PasarelaDePagos pasarelaDePagos(
      PropiedadesWompi propiedades, PropiedadesWompiPublicas propiedadesPublicas) {
    return new WompiClient(
        propiedades.secretoIntegridad(),
        propiedades.secretoEventos(),
        propiedadesPublicas.llavePublica(),
        propiedadesPublicas.ambiente());
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
      RepositorioInventario repositorioInventario,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj) {
    return new ProcesarEventoDePago(
        repositorioPagos, repositorioPedidos, repositorioInventario, pasarelaDePagos, reloj);
  }

  @Bean
  public RegistrarIdTransaccionWompi registrarIdTransaccionWompi(
      RepositorioPagos repositorioPagos) {
    return new RegistrarIdTransaccionWompi(repositorioPagos);
  }

  @Bean
  public ConciliarPagosPendientes conciliarPagosPendientes(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj,
      PropiedadesConciliacionWompi propiedades) {
    return new ConciliarPagosPendientes(
        repositorioPagos,
        repositorioPedidos,
        repositorioInventario,
        pasarelaDePagos,
        reloj,
        Duration.ofMinutes(propiedades.antiguedadMinimaMinutos()));
  }
}
