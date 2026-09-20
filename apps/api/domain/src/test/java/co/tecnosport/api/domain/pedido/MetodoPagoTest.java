package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Quién cobra cada método ({@code adr/0048}). Antes de que existieran dos pasarelas esto era un
 * booleano y no tenía prueba propia: no hacía falta, porque "pasa por la pasarela" solo podía
 * querer decir Wompi. Ahora sí, y sobre todo por la última de estas pruebas.
 */
class MetodoPagoTest {

  @Test
  void losMetodosDeWompiLosCobraWompi() {
    assertEquals(ProveedorDePago.WOMPI, MetodoPago.TARJETA.pasarela());
    assertEquals(ProveedorDePago.WOMPI, MetodoPago.PSE.pasarela());
    assertEquals(ProveedorDePago.WOMPI, MetodoPago.NEQUI.pasarela());
    assertEquals(ProveedorDePago.WOMPI, MetodoPago.BANCOLOMBIA.pasarela());
  }

  @Test
  void sistecreditoLoCobraSistecredito() {
    assertEquals(ProveedorDePago.SISTECREDITO, MetodoPago.SISTECREDITO.pasarela());
  }

  /** Los que resuelve el negocio a mano: un comprobante que alguien concilia, un mensajero. */
  @Test
  void loQueNoCobraUnaPasarelaLoDiceAsi() {
    assertEquals(ProveedorDePago.NINGUNO, MetodoPago.TRANSFERENCIA_MANUAL.pasarela());
    assertEquals(ProveedorDePago.NINGUNO, MetodoPago.CONTRAENTREGA.pasarela());
    assertFalse(MetodoPago.TRANSFERENCIA_MANUAL.laCobraUnaPasarela());
    assertFalse(MetodoPago.CONTRAENTREGA.laCobraUnaPasarela());
  }

  /**
   * <b>La prueba que justifica el archivo.</b> Addi apunta a Wompi aunque Wompi no lo ofrezca, y
   * eso no es un error pendiente de arreglar: es lo único que lo mantiene fuera del checkout.
   * {@code MetodosDePagoDisponibles} quita los métodos que cobra una pasarela y no están en su
   * lista de habilitados; Addi no está en la de Wompi, así que se cae ahí.
   *
   * <p>Reclasificarlo a {@code NINGUNO} —que describe mejor la realidad, porque hoy ninguna
   * pasarela lo cobra— lo sacaría del filtro y lo dejaría <b>ofrecido en todos los pedidos</b>.
   * Quien venga a "arreglar" esto que lea antes el {@code TODO} de docs/11-pagos-y-envios.md: la
   * salida es decidir qué se hace con el valor, no cambiarle el proveedor.
   */
  @Test
  void addiSigueApuntandoAWompiYEsLoQueLoMantieneFueraDelCheckout() {
    assertEquals(ProveedorDePago.WOMPI, MetodoPago.ADDI.pasarela());
    assertTrue(MetodoPago.ADDI.laCobraUnaPasarela());
  }
}
