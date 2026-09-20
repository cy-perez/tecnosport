package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.infrastructure.pago.SistecreditoClient;
import co.tecnosport.api.presentation.pago.PropiedadesWompiPublicas;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
@EnableConfigurationProperties(PropiedadesSistecredito.class)
public class ConfiguracionSistecredito {

  private static final Logger log = LoggerFactory.getLogger(ConfiguracionSistecredito.class);

  private static final String AMBIENTE_QUE_MUEVE_DINERO = "produccion";

  @Bean
  public PasarelaSistecredito pasarelaSistecredito(
      PropiedadesSistecredito propiedades, PropiedadesWompiPublicas propiedadesWompi) {
    exigirSandboxApagadoEnProduccion(propiedades, propiedadesWompi);
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
        Duration.ofSeconds(propiedades.timeoutSegundos()));
  }

  /**
   * Lo que esto evita es concreto: el modo sandbox hace que la pasarela responda {@code Approved}
   * sin pedirle un peso a nadie. Encendido en producción, cada pedido quedaría marcado como pagado
   * y se despacharía mercancía regalada, sin que nada fallara ni apareciera en ningún registro de
   * error. Es el único fallo de esta integración que no se nota hasta que se cuentan las cajas.
   */
  private void exigirSandboxApagadoEnProduccion(
      PropiedadesSistecredito propiedades, PropiedadesWompiPublicas propiedadesWompi) {
    if (propiedades.sandboxActivo()
        && AMBIENTE_QUE_MUEVE_DINERO.equals(propiedadesWompi.ambiente())) {
      throw new IllegalStateException(
          "tecnosport.sistecredito.sandbox-activo está encendido en un despliegue de producción"
              + " (WOMPI_AMBIENTE=produccion). El modo sandbox aprueba pagos que nadie pagó:"
              + " apágalo antes de arrancar.");
    }
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
