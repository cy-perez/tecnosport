package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.envio.AgregarCoberturaContraentrega;
import co.tecnosport.api.application.envio.ListarCoberturaContraentrega;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.QuitarCoberturaContraentrega;
import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mismo patrón que {@code ConfiguracionCarrito}/{@code ConfiguracionCatalogo}. */
@Configuration
@EnableConfigurationProperties(PropiedadesContraentrega.class)
public class ConfiguracionEnvio {

  @Bean
  public CriteriosContraentrega criteriosContraentrega(PropiedadesContraentrega propiedades) {
    Set<LineaCatalogo> categoriasExcluidas =
        propiedades.categoriasExcluidas().stream()
            .map(nombre -> LineaCatalogo.valueOf(nombre.trim().toUpperCase(Locale.ROOT)))
            .collect(Collectors.toSet());
    return new CriteriosContraentrega(
        propiedades.habilitada(), Dinero.deCop(propiedades.montoMaximo()), categoriasExcluidas);
  }

  @Bean
  public MetodosDePagoDisponibles metodosDePagoDisponibles(
      RepositorioProductos repositorioProductos,
      RepositorioCoberturaContraentrega repositorioCobertura,
      RepositorioPedidos repositorioPedidos,
      CriteriosContraentrega criteriosContraentrega) {
    return new MetodosDePagoDisponibles(
        repositorioProductos, repositorioCobertura, repositorioPedidos, criteriosContraentrega);
  }

  @Bean
  public ListarCoberturaContraentrega listarCoberturaContraentrega(
      RepositorioCoberturaContraentrega repositorio) {
    return new ListarCoberturaContraentrega(repositorio);
  }

  @Bean
  public AgregarCoberturaContraentrega agregarCoberturaContraentrega(
      RepositorioCoberturaContraentrega repositorio) {
    return new AgregarCoberturaContraentrega(repositorio);
  }

  @Bean
  public QuitarCoberturaContraentrega quitarCoberturaContraentrega(
      RepositorioCoberturaContraentrega repositorio) {
    return new QuitarCoberturaContraentrega(repositorio);
  }
}
