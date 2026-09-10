package co.tecnosport.api.bootstrap.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.application.pedido.CancelarPedido;
import co.tecnosport.api.application.pedido.ConciliarRecaudo;
import co.tecnosport.api.application.pedido.ConciliarTransferencia;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedido;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.application.pedido.ListarPedidosAdmin;
import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.application.pedido.ReintentarPago;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.pedido.VerificarContraentrega;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.bootstrap.compartido.PropiedadesLimitePedidos;
import co.tecnosport.api.bootstrap.legal.PropiedadesLegal;
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
@EnableConfigurationProperties({
  PropiedadesPedido.class,
  PropiedadesTransferenciaManual.class,
  PropiedadesLegal.class
})
public class ConfiguracionPedido {

  @Bean
  public CrearPedido crearPedido(
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      RepositorioPedidos repositorioPedidos,
      MetodosDePagoDisponibles metodosDePagoDisponibles,
      Reloj reloj,
      PropiedadesPedido propiedades,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimitePedidos propiedadesLimite,
      RepositorioAutorizaciones repositorioAutorizaciones,
      PropiedadesLegal propiedadesLegal) {
    return new CrearPedido(
        repositorioProductos,
        repositorioInventario,
        repositorioPedidos,
        metodosDePagoDisponibles,
        reloj,
        Duration.ofMinutes(propiedades.minutosReservaInventario()),
        Duration.ofHours(propiedades.horasVencimientoTransferencia()),
        limitadorDeIntentos,
        propiedadesLimite.cuentaMaximo(),
        Duration.ofMinutes(propiedadesLimite.cuentaMinutos()),
        repositorioAutorizaciones,
        propiedadesLegal.politicaDatosVersion());
  }

  @Bean
  public ListarPedidosAdmin listarPedidosAdmin(RepositorioPedidos repositorioPedidos) {
    return new ListarPedidosAdmin(repositorioPedidos);
  }

  @Bean
  public ConciliarTransferencia conciliarTransferencia(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    return new ConciliarTransferencia(repositorioPedidos, repositorioInventario, reloj);
  }

  @Bean
  public VerificarContraentrega verificarContraentrega(
      RepositorioPedidos repositorioPedidos, Reloj reloj) {
    return new VerificarContraentrega(repositorioPedidos, reloj);
  }

  @Bean
  public DespacharPedido despacharPedido(
      RepositorioPedidos repositorioPedidos, RepositorioEnvios repositorioEnvios, Reloj reloj) {
    return new DespacharPedido(repositorioPedidos, repositorioEnvios, reloj);
  }

  @Bean
  public CancelarPedido cancelarPedido(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    return new CancelarPedido(
        repositorioPedidos,
        repositorioInventario,
        repositorioReintegros,
        tope,
        enviadorDeCorreo,
        textos,
        reloj);
  }

  @Bean
  public MarcarEntregado marcarEntregado(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    return new MarcarEntregado(repositorioPedidos, repositorioInventario, reloj);
  }

  @Bean
  public RechazarEnEntrega rechazarEnEntrega(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    return new RechazarEnEntrega(repositorioPedidos, repositorioInventario, reloj);
  }

  @Bean
  public ConciliarRecaudo conciliarRecaudo(
      RepositorioPedidos repositorioPedidos, RepositorioEnvios repositorioEnvios, Reloj reloj) {
    return new ConciliarRecaudo(repositorioPedidos, repositorioEnvios, reloj);
  }

  @Bean
  public ConsultarSeguimientoPedido consultarSeguimientoPedido(
      RepositorioPedidos repositorioPedidos) {
    return new ConsultarSeguimientoPedido(repositorioPedidos);
  }

  @Bean
  public ReintentarPago reintentarPago(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj,
      PropiedadesPedido propiedades) {
    return new ReintentarPago(
        repositorioPedidos,
        repositorioInventario,
        reloj,
        Duration.ofMinutes(propiedades.minutosReservaInventario()));
  }
}
