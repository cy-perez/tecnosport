package co.tecnosport.api.bootstrap.reintegro;

import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.application.reversion.RepositorioSolicitudesReversion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El tope de lo que un pedido puede devolver, uno solo para todo el sistema.
 *
 * <p>Configuración propia y no un {@code bean} más de {@code ConfiguracionRetracto}, por la misma
 * razón que llevó el calendario hábil a {@code ConfiguracionCalendario}: lo comparten cuatro
 * consumidores —retracto, garantía, reversión y cancelación— y ninguno de ellos es el dueño. Dos
 * topes serían dos reglas de cuánto se puede devolver, y el día que divergieran, el mismo pedido
 * admitiría montos distintos según por dónde le entrara la devolución.
 */
@Configuration
public class ConfiguracionReintegro {

  @Bean
  public TopeDeReintegro topeDeReintegro(
      RepositorioReintegros repositorioReintegros,
      RepositorioSolicitudesReversion repositorioReversiones) {
    return new TopeDeReintegro(repositorioReintegros, repositorioReversiones);
  }
}
