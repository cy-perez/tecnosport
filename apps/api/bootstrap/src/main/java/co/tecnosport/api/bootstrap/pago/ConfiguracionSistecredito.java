package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pago.ConciliarPagosSistecredito;
import co.tecnosport.api.application.pago.CrearIntentoDePagoSistecredito;
import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.application.pago.ProcesarNotificacionSistecredito;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.infrastructure.pago.SistecreditoClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Arma el cliente de Sistecrédito y monta el freno de seguridad del modo sandbox ({@code
 * adr/0048}).
 *
 * <p><b>Por qué el freno mira una propiedad de Wompi.</b> Este backend no tiene un perfil de
 * producción: el único perfil que existe es {@code e2e}, y {@code SCOrigen} no sirve como señal
 * porque esta cuenta de Sistecrédito solo tiene credenciales productivas —vale {@code Production}
 * también en desarrollo—. La única marca por despliegue que ya distingue "esta instancia mueve
 * dinero de verdad" es {@code WOMPI_AMBIENTE}, que producción pone en {@code produccion} y
 * desarrollo deja en {@code sandbox}. Se reutiliza en vez de inventar una segunda señal paralela
 * que podría contradecirla.
 *
 * <p>TODO (técnico, no dato de negocio): cuando exista un perfil de Spring para producción, el
 * freno debería colgar de él y no de la configuración de otra pasarela.
 */
@Configuration
@EnableConfigurationProperties({
  PropiedadesSistecredito.class,
  PropiedadesConciliacionSistecredito.class
})
public class ConfiguracionSistecredito {

  private static final Logger log = LoggerFactory.getLogger(ConfiguracionSistecredito.class);

  /**
   * <b>Lista blanca, no lista negra.</b> Los perfiles con los que este repositorio declara "este
   * despliegue no mueve dinero de verdad": los tres que existen —{@code local}, {@code dev} y
   * {@code e2e}— más {@code pruebas}, por si algún día alguien nombra así un entorno.
   *
   * <p>Estaba escrito al revés —negar el arranque solo si el ambiente era exactamente {@code
   * "produccion"}— y esa forma falla <b>abierta</b>: {@code production}, {@code PRODUCCION}, {@code
   * prod}, un typo, o un despliegue nuevo donde nadie fijó la variable, dejaban pasar el arranque
   * con el sandbox encendido. Es el mismo patrón que la regla dura #1 documenta para el guardián de
   * capas que nunca disparaba: un guardián que falla abierto ante un descuido no es un guardián.
   *
   * <p><b>Y desde el 21 de septiembre de 2026 la señal es el perfil y no {@code
   * WOMPI_AMBIENTE}.</b> Colgaba de la configuración de <i>la otra</i> pasarela por una razón
   * honesta y escrita —no había perfil de producción y esa era la única marca por despliegue
   * disponible—, con su {@code TODO} pidiendo enderezarlo. Enderezarlo no exigió inventar un perfil
   * nuevo: basta con invertir la pregunta. No se pregunta "¿es producción?", que obliga a que
   * alguien se acuerde de marcarla; se pregunta "¿está declarado como despliegue de pruebas?", y
   * <b>un despliegue sin ningún perfil ya no arranca con el sandbox encendido</b>. Antes sí lo
   * hacía, porque {@code WOMPI_AMBIENTE} vale {@code sandbox} por omisión: el caso más peligroso
   * —producción recién montada, nadie fijó las variables— era justo el que pasaba el freno.
   */
  private static final Set<String> PERFILES_SIN_DINERO_REAL =
      Set.of("local", "dev", "e2e", "pruebas");

  @Bean
  public PasarelaSistecredito pasarelaSistecredito(
      PropiedadesSistecredito propiedades, Environment entorno) {
    exigirSandboxApagadoFueraDeUnDespliegueDePruebas(propiedades, entorno);
    avisarDelModoSandbox(propiedades);
    if (!propiedades.habilitado()) {
      // Un cliente que no puede llamar a nadie, para que el contexto levante igual con el método
      // apagado. Nadie lo va a usar: `MetodosDePagoDisponibles` no ofrece SISTECREDITO y
      // `CrearPedido` lo rechaza antes.
      return new SistecreditoApagado();
    }
    return new SistecreditoClient(
        propiedades.urlBase(),
        propiedades.llaveSuscripcion(),
        propiedades.storeId(),
        propiedades.vendorId(),
        propiedades.ambiente(),
        propiedades.metodoDePagoId(),
        Duration.ofSeconds(propiedades.timeoutSegundos()),
        propiedades.sondeoIntentos(),
        Duration.ofMillis(propiedades.sondeoEsperaMilis()));
  }

  @Bean
  public CrearIntentoDePagoSistecredito crearIntentoDePagoSistecredito(
      RepositorioPedidos repositorioPedidos,
      RepositorioPagos repositorioPagos,
      PasarelaSistecredito pasarelaSistecredito,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj,
      PropiedadesSistecredito propiedades) {
    return new CrearIntentoDePagoSistecredito(
        repositorioPedidos,
        repositorioPagos,
        pasarelaSistecredito,
        enTransaccionPropia,
        reloj,
        propiedades.urlRespuesta(),
        propiedades.urlConfirmacion(),
        propiedades.sandboxActivo(),
        propiedades.sandboxEstado());
  }

  @Bean
  public ProcesarNotificacionSistecredito procesarNotificacionSistecredito(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaSistecredito pasarelaSistecredito,
      Reloj reloj) {
    return new ProcesarNotificacionSistecredito(
        repositorioPagos, repositorioPedidos, repositorioInventario, pasarelaSistecredito, reloj);
  }

  @Bean
  public ConciliarPagosSistecredito conciliarPagosSistecredito(
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      PasarelaSistecredito pasarelaSistecredito,
      Reloj reloj,
      PropiedadesConciliacionSistecredito propiedades) {
    return new ConciliarPagosSistecredito(
        repositorioPagos,
        repositorioPedidos,
        repositorioInventario,
        pasarelaSistecredito,
        reloj,
        Duration.ofMinutes(propiedades.antiguedadMinimaMinutos()));
  }

  /**
   * Lo que esto evita es concreto: el modo sandbox hace que la pasarela responda {@code Approved}
   * sin pedirle un peso a nadie. Encendido en producción, cada pedido quedaría marcado como pagado
   * y se despacharía mercancía regalada, sin que nada fallara ni apareciera en ningún registro de
   * error. Es el único fallo de esta integración que no se nota hasta que se cuentan las cajas.
   */
  private void exigirSandboxApagadoFueraDeUnDespliegueDePruebas(
      PropiedadesSistecredito propiedades, Environment entorno) {
    if (!propiedades.sandboxActivo()) {
      return;
    }
    String[] perfiles = entorno.getActiveProfiles();
    if (Arrays.stream(perfiles).anyMatch(PERFILES_SIN_DINERO_REAL::contains)) {
      return;
    }
    throw new IllegalStateException(
        "tecnosport.sistecredito.sandbox-activo está encendido y los perfiles activos son "
            + (perfiles.length == 0 ? "ninguno" : Arrays.toString(perfiles))
            + ", que no incluyen ninguno de "
            + PERFILES_SIN_DINERO_REAL
            + ". El modo sandbox aprueba pagos que nadie pagó, así que solo se permite en un"
            + " despliegue declarado como de pruebas: apágalo, o declara el perfil.");
  }

  private void avisarDelModoSandbox(PropiedadesSistecredito propiedades) {
    if (propiedades.sandboxActivo()) {
      log.warn(
          "Sistecrédito en MODO SANDBOX: las transacciones simulan el estado {} y no cobran nada."
              + " Ninguna venta con este medio de pago es real mientras esto siga encendido.",
          propiedades.sandboxEstado());
    }
  }
}
