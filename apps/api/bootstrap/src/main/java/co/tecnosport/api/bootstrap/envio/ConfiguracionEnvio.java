package co.tecnosport.api.bootstrap.envio;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AplicarEventoDeEnvio;
import co.tecnosport.api.application.envio.ConciliarEnvios;
import co.tecnosport.api.application.envio.ConsultorDeSeguimiento;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.LectorEventoDeEnvio;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.RecibirEventoDeEnvio;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.envio.VerificadorFirmaEnvio;
import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.infrastructure.envio.OrigenDespacho;
import co.tecnosport.api.infrastructure.envio.SkydropxClient;
import co.tecnosport.api.infrastructure.envio.VerificadorFirmaEnvioHmac;
import co.tecnosport.api.infrastructure.envio.siembra.CotizadorEnvioSembrado;
import co.tecnosport.api.presentation.envio.PropiedadesWebhookEnvio;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Mismo patrón que {@code ConfiguracionCarrito}/{@code ConfiguracionCatalogo}. */
@Configuration
@EnableConfigurationProperties({
  PropiedadesContraentrega.class,
  PropiedadesSkydropx.class,
  PropiedadesSeguimientoEnvios.class,
  PropiedadesWebhookEnvio.class,
  PropiedadesOrigen.class
})
public class ConfiguracionEnvio {

  /**
   * El cotizador real. Hoy falla cerrado —el mapeo con Skydropx no está confirmado— y eso para el
   * checkout significa "solo recogida en el punto", que es lo que adr/0021 decidió para cuando no
   * hay tarifa. Se registra igual, y no se deja el puerto sin implementación, porque el día que se
   * confirme el mapeo no hay que tocar el cableado.
   *
   * <p>{@code @Profile("!e2e")} y no {@code @ConditionalOnMissingBean}: los recorridos de
   * Playwright sustituyen este bean por {@link CotizadorEnvioSembrado}, y de las dos formas de
   * hacerlo esta es la que no se puede leer al revés. Con dos beans registrados y una precedencia,
   * alguien tiene que saber cuál gana; aquí solo existe uno de los dos y el contenedor no arranca
   * si el perfil dice otra cosa.
   */
  @Bean
  @Profile("!e2e")
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
            origen.departamento(),
            origen.ciudad(),
            origen.ciudadDane(),
            origen.codigoPostal()),
        Duration.ofSeconds(skydropx.cotizacionTimeoutSegundos()),
        skydropx.cotizacionIntentos(),
        INTERVALO_SONDEO,
        reloj);
  }

  /**
   * El cotizador de los recorridos de Playwright, y de nada más. El porqué entero está en {@link
   * CotizadorEnvioSembrado}; lo que importa aquí es que el perfil {@code e2e} no se activa en
   * ningún despliegue: {@code bootRun} fija {@code local}, el jar de Cloud Run no fija ninguno, y
   * el único sitio que pide {@code e2e} es el flujo {@code recorridos} de integración continua.
   */
  @Bean
  @Profile("e2e")
  public CotizadorEnvio cotizadorEnvioSembrado(Reloj reloj) {
    return new CotizadorEnvioSembrado(reloj);
  }

  /**
   * Entre sondeo y sondeo. Por debajo del medio segundo no tiene sentido: el limitador de 2
   * peticiones por segundo lo frenaría igual, y el hilo esperaría en otro sitio.
   */
  private static final Duration INTERVALO_SONDEO = Duration.ofMillis(500);

  /**
   * La firma del webhook, con el algoritmo confirmado en la documentación oficial el 14 de
   * septiembre de 2026 (docs/13-skydropx-capacidades.md, sección 6.1).
   *
   * <p>Se cablea aquí, como {@link SkydropxClient}, y no con {@code @Component}: necesita el
   * secreto, y un adaptador que se anota a sí mismo tendría que ir a buscarlo.
   *
   * <p>Que el bean exista no significa que verifique: mientras {@code SKYDROPX_SECRETO_WEBHOOK} sea
   * el marcador de desarrollo, la firma nunca cuadra y todo evento se descarta. Lo que cambió es el
   * motivo — antes faltaba el algoritmo, ahora falta el secreto del panel, y eso es una variable de
   * entorno y no un despliegue.
   */
  @Bean
  public VerificadorFirmaEnvio verificadorFirmaEnvio(PropiedadesWebhookEnvio propiedades) {
    return new VerificadorFirmaEnvioHmac(propiedades.secreto());
  }

  /**
   * De los tres puertos del seguimiento ya solo dos fallan cerrado: leer el evento y consultar el
   * rastreo, que siguen sin poderse medir contra un evento real (docs/13-skydropx-capacidades.md,
   * sección 6). Se registran igual para que el día que se confirmen sea cambiar una implementación
   * y no montar el cableado.
   */
  @Bean
  public ConciliarEnvios conciliarEnvios(
      RepositorioEnvios repositorioEnvios,
      ConsultorDeSeguimiento consultor,
      AplicarEventoDeEnvio aplicarEvento,
      Reloj reloj,
      PropiedadesSeguimientoEnvios propiedades) {
    return new ConciliarEnvios(
        repositorioEnvios,
        consultor,
        aplicarEvento,
        reloj,
        Duration.ofHours(propiedades.antiguedadMinimaHoras()),
        propiedades.maximoPorCorrida());
  }

  @Bean
  public RecibirEventoDeEnvio recibirEventoDeEnvio(
      VerificadorFirmaEnvio verificadorFirma,
      LectorEventoDeEnvio lector,
      AplicarEventoDeEnvio aplicarEvento) {
    return new RecibirEventoDeEnvio(verificadorFirma, lector, aplicarEvento);
  }

  @Bean
  public AplicarEventoDeEnvio aplicarEventoDeEnvio(
      RepositorioEnvios repositorioEnvios,
      RepositorioPedidos repositorioPedidos,
      MarcarEntregado marcarEntregado,
      RechazarEnEntrega rechazarEnEntrega,
      Reloj reloj) {
    return new AplicarEventoDeEnvio(
        repositorioEnvios, repositorioPedidos, marcarEntregado, rechazarEnEntrega, reloj);
  }

  @Bean
  public CotizarEnvio cotizarEnvio(
      RepositorioProductos repositorioProductos, CotizadorEnvio cotizadorEnvio, Reloj reloj) {
    return new CotizarEnvio(repositorioProductos, cotizadorEnvio, reloj);
  }

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
      CotizarEnvio cotizarEnvio,
      RepositorioPedidos repositorioPedidos,
      CriteriosContraentrega criteriosContraentrega) {
    return new MetodosDePagoDisponibles(
        repositorioProductos, cotizarEnvio, repositorioPedidos, criteriosContraentrega);
  }
}
