package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.pago.ListarPagosSinPedido;
import co.tecnosport.api.application.pago.RegistrarReintegroDePagoSinPedido;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los pagos aprobados sin pedido que los esperara: comunes a Wompi y a Sistecrédito, por eso no
 * viven en la configuración de ninguna de las dos pasarelas.
 */
@Configuration
public class ConfiguracionPagosSinPedido {

  @Bean
  public ListarPagosSinPedido listarPagosSinPedido(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros) {
    return new ListarPagosSinPedido(repositorioPagos, repositorioPedidos, repositorioReintegros);
  }

  @Bean
  public RegistrarReintegroDePagoSinPedido registrarReintegroDePagoSinPedido(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    return new RegistrarReintegroDePagoSinPedido(
        repositorioPagos,
        repositorioPedidos,
        repositorioReintegros,
        enviadorDeCorreo,
        textos,
        reloj);
  }
}
