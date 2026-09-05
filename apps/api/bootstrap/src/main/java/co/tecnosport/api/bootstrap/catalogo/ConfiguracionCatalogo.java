package co.tecnosport.api.bootstrap.catalogo;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.BuscarProductos;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.ListarAtributos;
import co.tecnosport.api.application.catalogo.ListarCategorias;
import co.tecnosport.api.application.catalogo.ListarMarcas;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VerFichaDeProducto;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
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

  @Bean
  public ListarCategorias listarCategorias(RepositorioCategorias repositorioCategorias) {
    return new ListarCategorias(repositorioCategorias);
  }

  @Bean
  public ListarMarcas listarMarcas(RepositorioMarcas repositorioMarcas) {
    return new ListarMarcas(repositorioMarcas);
  }

  @Bean
  public ListarProductosAdmin listarProductosAdmin(RepositorioProductos repositorioProductos) {
    return new ListarProductosAdmin(repositorioProductos);
  }

  @Bean
  public CrearProducto crearProducto(
      RepositorioProductos repositorioProductos,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias) {
    return new CrearProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
  }

  @Bean
  public VerProductoAdmin verProductoAdmin(RepositorioProductos repositorioProductos) {
    return new VerProductoAdmin(repositorioProductos);
  }

  @Bean
  public EditarProducto editarProducto(
      RepositorioProductos repositorioProductos,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias) {
    return new EditarProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
  }

  @Bean
  public ListarAtributos listarAtributos(RepositorioAtributos repositorioAtributos) {
    return new ListarAtributos(repositorioAtributos);
  }

  @Bean
  public AgregarVariante agregarVariante(
      RepositorioProductos repositorioProductos,
      RepositorioAtributos repositorioAtributos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    return new AgregarVariante(
        repositorioProductos, repositorioAtributos, repositorioInventario, reloj);
  }
}
