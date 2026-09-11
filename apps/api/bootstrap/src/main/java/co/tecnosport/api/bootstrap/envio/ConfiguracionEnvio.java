package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AgregarCoberturaContraentrega;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.ListarCoberturaContraentrega;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.QuitarCoberturaContraentrega;
import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.infrastructure.envio.OrigenDespacho;
import co.tecnosport.api.infrastructure.envio.SkydropxClient;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mismo patrón que {@code ConfiguracionCarrito}/{@code ConfiguracionCatalogo}. */
@Configuration
@EnableConfigurationProperties({
  PropiedadesContraentrega.class,
  PropiedadesSkydropx.class,
  PropiedadesOrigen.class
})
public class ConfiguracionEnvio {

  /**
   * El cotizador real. Hoy falla cerrado —el mapeo con Skydropx no está confirmado— y eso para el
   * checkout significa "solo recogida en el punto", que es lo que adr/0021 decidió para cuando no
   * hay tarifa. Se registra igual, y no se deja el puerto sin implementación, porque el día que se
   * confirme el mapeo no hay que tocar el cableado.
   */
  @Bean
  public CotizadorEnvio cotizadorEnvio(
      PropiedadesSkydropx skydropx, PropiedadesOrigen origen, Reloj reloj) {
    return new SkydropxClient(
        URI.create(skydropx.urlBase()),
        skydropx.clientId(),
        skydropx.clientSecret(),
        new OrigenDespacho(
            origen.nombre(),
            origen.telefono(),
            origen.direccion(),
            origen.ciudadDane(),
            origen.codigoPostal()),
        Duration.ofSeconds(skydropx.cotizacionTimeoutSegundos()),
        skydropx.cotizacionIntentos(),
        INTERVALO_SONDEO,
        reloj);
  }

  /**
   * Entre sondeo y sondeo. Por debajo del medio segundo no tiene sentido: el limitador de 2
   * peticiones por segundo lo frenaría igual, y el hilo esperaría en otro sitio.
   */
  private static final Duration INTERVALO_SONDEO = Duration.ofMillis(500);

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
