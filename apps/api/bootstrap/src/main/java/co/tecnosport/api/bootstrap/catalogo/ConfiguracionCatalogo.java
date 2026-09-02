package co.tecnosport.api.bootstrap.catalogo;

import co.tecnosport.api.application.catalogo.BuscarProductos;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VerFichaDeProducto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code application} es framework-free a propósito: sus casos de uso no llevan {@code @Component}.
 * Aquí, en {@code bootstrap}, es donde se decide qué implementación de cada puerto usar y se
 * registran los casos de uso como beans — docs/01-arquitectura.md: "quién implementa el repositorio
 * lo decide bootstrap".
 */
@Configuration
public class ConfiguracionCatalogo {

  @Bean
  public BuscarProductos buscarProductos(RepositorioProductos repositorioProductos) {
    return new BuscarProductos(repositorioProductos);
  }

  @Bean
  public VerFichaDeProducto verFichaDeProducto(RepositorioProductos repositorioProductos) {
    return new VerFichaDeProducto(repositorioProductos);
  }
}
