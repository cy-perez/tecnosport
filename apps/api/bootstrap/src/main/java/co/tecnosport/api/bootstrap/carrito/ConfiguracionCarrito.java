package co.tecnosport.api.bootstrap.carrito;

import co.tecnosport.api.application.carrito.ActualizarCantidadDeLinea;
import co.tecnosport.api.application.carrito.AgregarLineaAlCarrito;
import co.tecnosport.api.application.carrito.CrearCarrito;
import co.tecnosport.api.application.carrito.EliminarLineaDelCarrito;
import co.tecnosport.api.application.carrito.RepositorioCarrito;
import co.tecnosport.api.application.carrito.VerCarrito;
import co.tecnosport.api.application.compartido.Reloj;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mismo patrón que {@code ConfiguracionCatalogo}: application es framework-free a propósito. */
@Configuration
public class ConfiguracionCarrito {

  @Bean
  public CrearCarrito crearCarrito(RepositorioCarrito repositorioCarrito, Reloj reloj) {
    return new CrearCarrito(repositorioCarrito, reloj);
  }

  @Bean
  public VerCarrito verCarrito(RepositorioCarrito repositorioCarrito) {
    return new VerCarrito(repositorioCarrito);
  }

  @Bean
  public AgregarLineaAlCarrito agregarLineaAlCarrito(RepositorioCarrito repositorioCarrito) {
    return new AgregarLineaAlCarrito(repositorioCarrito);
  }

  @Bean
  public ActualizarCantidadDeLinea actualizarCantidadDeLinea(
      RepositorioCarrito repositorioCarrito) {
    return new ActualizarCantidadDeLinea(repositorioCarrito);
  }

  @Bean
  public EliminarLineaDelCarrito eliminarLineaDelCarrito(RepositorioCarrito repositorioCarrito) {
    return new EliminarLineaDelCarrito(repositorioCarrito);
  }
}
