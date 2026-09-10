package co.tecnosport.api.bootstrap.retracto;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.application.retracto.RecibirProductoDevuelto;
import co.tecnosport.api.application.retracto.RegistrarReintegro;
import co.tecnosport.api.application.retracto.RegistrarRetracto;
import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionRetracto {

  @Bean
  public RegistrarRetracto registrarRetracto(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      CalendarioHabil calendarioHabil,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    return new RegistrarRetracto(
        repositorioSolicitudes,
        repositorioPedidos,
        calendarioHabil,
        enviadorDeCorreo,
        textos,
        reloj);
  }

  @Bean
  public RecibirProductoDevuelto recibirProductoDevuelto(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    return new RecibirProductoDevuelto(
        repositorioSolicitudes, repositorioPedidos, repositorioInventario, reloj);
  }

  @Bean
  public RegistrarReintegro registrarReintegro(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      RepositorioReintegros repositorioReintegros,
      TopeDeReintegro tope,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj) {
    return new RegistrarReintegro(
        repositorioSolicitudes,
        repositorioPedidos,
        repositorioReintegros,
        tope,
        enviadorDeCorreo,
        textos,
        reloj);
  }
}
