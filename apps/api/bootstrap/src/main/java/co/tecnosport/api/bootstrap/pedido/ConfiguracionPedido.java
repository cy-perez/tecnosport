package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.ConciliarTransferencia;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.ListarPedidosAdmin;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.presentation.pedido.PropiedadesTransferenciaManual;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mismo patrón que {@code ConfiguracionCarrito}/{@code ConfiguracionCatalogo}. {@code
 * PropiedadesTransferenciaManual} se activa aquí, no donde se consume ({@code
 * MapeadorRespuestasPedido}, un {@code @Component} normal que Spring ya autoconecta): activar sus
 * propiedades es trabajo de bootstrap, igual que con Wompi.
 */
@Configuration
@EnableConfigurationProperties({PropiedadesPedido.class, PropiedadesTransferenciaManual.class})
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

  @Bean
  public ListarPedidosAdmin listarPedidosAdmin(RepositorioPedidos repositorioPedidos) {
    return new ListarPedidosAdmin(repositorioPedidos);
  }

  @Bean
  public ConciliarTransferencia conciliarTransferencia(
      RepositorioPedidos repositorioPedidos, Reloj reloj) {
    return new ConciliarTransferencia(repositorioPedidos, reloj);
  }
}
