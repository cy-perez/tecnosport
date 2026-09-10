package co.tecnosport.api.bootstrap.retracto;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.retracto.RecibirProductoDevuelto;
import co.tecnosport.api.application.retracto.RegistrarReintegro;
import co.tecnosport.api.application.retracto.RegistrarRetracto;
import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionRetracto {

  /**
   * TODO: FESTIVOS_COLOMBIA — cargar el calendario oficial de festivos por ano (Ley 51 de 1983, con
   * los que se trasladan al lunes siguiente).
   *
   * <p>Mientras no exista, el calendario solo conoce los fines de semana, y eso <b>no</b> hace que
   * el sistema mienta: {@code PlazoDeRetracto} responde {@code INDETERMINADO} en vez de afirmar que
   * un plazo vencio cuando no puede saberlo. El dia que se cargue, los veredictos indeterminados
   * pasan a ser definitivos sin tocar una linea de logica.
   */
  @Bean
  public CalendarioHabil calendarioHabil() {
    return CalendarioHabil.sinFestivosCargados();
  }

  @Bean
  public RegistrarRetracto registrarRetracto(
      RepositorioSolicitudesRetracto repositorioSolicitudes,
      RepositorioPedidos repositorioPedidos,
      CalendarioHabil calendarioHabil,
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj) {
    return new RegistrarRetracto(
        repositorioSolicitudes, repositorioPedidos, calendarioHabil, enviadorDeCorreo, reloj);
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
      EnviadorDeCorreo enviadorDeCorreo,
      Reloj reloj) {
    return new RegistrarReintegro(
        repositorioSolicitudes, repositorioPedidos, repositorioReintegros, enviadorDeCorreo, reloj);
  }
}
