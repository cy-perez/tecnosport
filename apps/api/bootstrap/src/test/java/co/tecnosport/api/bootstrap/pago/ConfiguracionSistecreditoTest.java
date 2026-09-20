package co.tecnosport.api.bootstrap.pago;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.application.pago.SistecreditoNoRespondeException;
import co.tecnosport.api.infrastructure.pago.SistecreditoClient;
import co.tecnosport.api.presentation.pago.PropiedadesWompiPublicas;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * El freno de seguridad de {@code adr/0048}, que es lo único de esta integración que puede salir
 * mal sin que nada falle ni aparezca en ningún registro de error: el modo sandbox encendido en
 * producción aprueba pagos que nadie pagó, y la mercancía sale.
 */
class ConfiguracionSistecreditoTest {

  private final ConfiguracionSistecredito configuracion = new ConfiguracionSistecredito();

  @Test
  void elModoSandboxEncendidoEnUnAmbienteDePruebasReconocidoSiArranca() {
    assertDoesNotThrow(
        () -> configuracion.pasarelaSistecredito(propiedades(true, true), wompi("sandbox")));
    assertDoesNotThrow(
        () -> configuracion.pasarelaSistecredito(propiedades(true, true), wompi("pruebas")));
  }

  /**
   * <b>La prueba que justifica la lista blanca.</b> El freno estaba escrito como lista negra —negar
   * solo si el ambiente era exactamente "produccion"— y así un typo, otra grafía o un despliegue
   * donde nadie fijó la variable lo dejaban pasar. Lo que hay al otro lado es despachar mercancía
   * regalada sin una sola línea de error.
   */
  @Test
  void unAmbienteQueNoSeReconoceComoDePruebasNoDejaEncenderElSandbox() {
    for (String ambiente : new String[] {"production", "PRODUCCION", "prod", "produccion", "qa"}) {
      IllegalStateException error =
          assertThrows(
              IllegalStateException.class,
              () -> configuracion.pasarelaSistecredito(propiedades(true, true), wompi(ambiente)),
              "el ambiente \"" + ambiente + "\" no debería dejar encender el sandbox");
      assertTrue(error.getMessage().contains("sandbox-activo"));
    }
  }

  @Test
  void conElMetodoApagadoNoSeConstruyeUnClienteContraLaPasarelaReal() {
    PasarelaSistecredito pasarela =
        configuracion.pasarelaSistecredito(propiedades(false, false), wompi("sandbox"));

    assertInstanceOf(SistecreditoApagado.class, pasarela);
  }

  /**
   * Revienta en vez de devolver un vacío amable: llegar aquí con el método apagado significa que
   * falló una comprobación de {@code MetodosDePagoDisponibles} o de {@code CrearPedido}, y eso
   * tiene que verse, no disimularse como una caída del proveedor.
   */
  @Test
  void elMetodoApagadoRevientaSiAlguienIntentaCobrarConEl() {
    PasarelaSistecredito pasarela =
        configuracion.pasarelaSistecredito(propiedades(false, false), wompi("sandbox"));

    assertThrows(SistecreditoNoRespondeException.class, () -> pasarela.crear(null));
  }

  @Test
  void habilitadoYSinSandboxConstruyeElClienteDeVerdad() {
    PasarelaSistecredito pasarela =
        configuracion.pasarelaSistecredito(propiedades(true, false), wompi("produccion"));

    assertInstanceOf(SistecreditoClient.class, pasarela);
  }

  private PropiedadesSistecredito propiedades(boolean habilitado, boolean sandboxActivo) {
    return new PropiedadesSistecredito(
        habilitado,
        "https://api.credinet.co/pay",
        "llave",
        "store",
        "vendor",
        "Production",
        "https://tecnosport.co/checkout/sistecredito/retorno",
        "https://api.tecnosport.co/api/v1/pagos/sistecredito/confirmacion",
        2,
        10,
        10,
        700,
        sandboxActivo,
        "Approved",
        habilitado ? new BigDecimal("30000") : null);
  }

  private PropiedadesWompiPublicas wompi(String ambiente) {
    return new PropiedadesWompiPublicas("pub_test_placeholder", ambiente);
  }
}
