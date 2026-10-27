package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pago.ListarPagosSinTransaccion;
import co.tecnosport.api.application.pago.MarcarPagoSinTransaccionRevisado;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.ListarPedidosDetenidosEnPreparacion;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Las alertas del panel de pedidos ({@code AdminAlertasControlador}). */
@Configuration
@EnableConfigurationProperties(PropiedadesAlertasDePedidos.class)
public class ConfiguracionAlertasDePedidos {

  @Bean
  public ListarPagosSinTransaccion listarPagosSinTransaccion(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      Reloj reloj,
      PropiedadesAlertasDePedidos propiedades) {
    return new ListarPagosSinTransaccion(
        repositorioPagos,
        repositorioPedidos,
        reloj,
        Duration.ofHours(propiedades.pagoSinTransaccionHoras()));
  }

  @Bean
  public ListarPedidosDetenidosEnPreparacion listarPedidosDetenidosEnPreparacion(
      RepositorioPedidos repositorioPedidos, Reloj reloj, PropiedadesAlertasDePedidos propiedades) {
    return new ListarPedidosDetenidosEnPreparacion(
        repositorioPedidos, reloj, Duration.ofDays(propiedades.enPreparacionDias()));
  }

  @Bean
  public MarcarPagoSinTransaccionRevisado marcarPagoSinTransaccionRevisado(
      RepositorioPagos repositorioPagos, Reloj reloj) {
    return new MarcarPagoSinTransaccionRevisado(repositorioPagos, reloj);
  }
}
