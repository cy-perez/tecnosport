package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mismo patrón que {@code ConfiguracionCarrito}/{@code ConfiguracionCatalogo}. */
@Configuration
@EnableConfigurationProperties(PropiedadesPedido.class)
public class ConfiguracionPedido {

  @Bean
  public CrearPedido crearPedido(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      RepositorioPedidos repositorioPedidos,
      Reloj reloj,
      PropiedadesPedido propiedades) {
    return new CrearPedido(
        repositorioProductos,
        repositorioInventario,
        repositorioPedidos,
        reloj,
        Duration.ofMinutes(propiedades.minutosReservaInventario()),
        Duration.ofHours(propiedades.horasVencimientoTransferencia()));
  }
}
