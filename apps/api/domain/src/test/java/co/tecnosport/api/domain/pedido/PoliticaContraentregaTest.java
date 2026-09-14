package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PoliticaContraentregaTest {

  /**
   * Mínimo de un peso en los criterios compartidos: las pruebas que ya existían afirman sobre el
   * techo y sobre las otras reglas, y un piso real les cambiaría el escenario sin que nadie lo
   * pidiera. El piso tiene sus propias pruebas, abajo, con sus propios criterios.
   */
  private static final CriteriosContraentrega CRITERIOS =
      new CriteriosContraentrega(
          true, Dinero.deCop(1), Dinero.deCop(1_000_000), Set.of(LineaCatalogo.CELULARES));

  private static final CriteriosContraentrega CON_PISO =
      new CriteriosContraentrega(true, Dinero.deCop(2_000), Dinero.deCop(2_000_000), Set.of());

  @Test
  void disponibleCuandoCumpleTodasLasReglas() {
    assertTrue(
        PoliticaContraentrega.disponible(
            CRITERIOS, Dinero.deCop(200_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  @Test
  void noDisponibleSiContraentregaEstaDeshabilitadaGlobalmente() {
    CriteriosContraentrega deshabilitada =
        new CriteriosContraentrega(false, Dinero.deCop(1), Dinero.deCop(1_000_000), Set.of());
    assertFalse(
        PoliticaContraentrega.disponible(
            deshabilitada, Dinero.deCop(200_000), Set.of(), true, false));
  }

  @Test
  void noDisponibleSiLaCiudadNoEstaCubierta() {
    assertFalse(
        PoliticaContraentrega.disponible(
            CRITERIOS, Dinero.deCop(200_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), false, false));
  }

  @Test
  void noDisponibleSiElCompradorTieneUnRechazoPrevio() {
    assertFalse(
        PoliticaContraentrega.disponible(
            CRITERIOS, Dinero.deCop(200_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, true));
  }

  @Test
  void noDisponibleSiElTotalSuperaElMontoMaximo() {
    assertFalse(
        PoliticaContraentrega.disponible(
            CRITERIOS, Dinero.deCop(1_000_001), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  @Test
  void elTotalIgualAlMontoMaximoSiEstaDisponible() {
    assertTrue(
        PoliticaContraentrega.disponible(
            CRITERIOS, Dinero.deCop(1_000_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  @Test
  void noDisponibleSiElCarritoTieneUnaCategoriaExcluida() {
    assertFalse(
        PoliticaContraentrega.disponible(
            CRITERIOS,
            Dinero.deCop(200_000),
            Set.of(LineaCatalogo.ROPA_Y_CALZADO, LineaCatalogo.CELULARES),
            true,
            false));
  }

  /**
   * El piso es un límite de quien recauda, no del negocio: por debajo de cierto valor la
   * transportadora simplemente no cobra en la puerta. Ofrecer contraentrega ahí sería prometer un
   * medio de pago que nadie puede ejecutar, y el comprador se enteraría con el mensajero enfrente.
   */
  @Test
  void noDisponibleSiElTotalNoLlegaAlMontoMinimo() {
    assertFalse(
        PoliticaContraentrega.disponible(
            CON_PISO, Dinero.deCop(1_999), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  /** Los dos extremos son inclusivos, y se afirman los dos: el techo ya lo estaba. */
  @Test
  void elTotalIgualAlMontoMinimoSiEstaDisponible() {
    assertTrue(
        PoliticaContraentrega.disponible(
            CON_PISO, Dinero.deCop(2_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  /**
   * El total incluye el flete, así que un pedido barato con envío puede cruzar el piso que la
   * mercancía sola no cruzaba. Es a propósito: la transportadora cobra el total en la puerta, no el
   * subtotal.
   */
  @Test
  void elRangoSeMideContraElTotalConElFleteDentro() {
    assertTrue(
        PoliticaContraentrega.disponible(
            CON_PISO, Dinero.deCop(2_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
    assertFalse(
        PoliticaContraentrega.disponible(
            CON_PISO, Dinero.deCop(2_000_001), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  /**
   * Un rango invertido no deja pasar ningún pedido, y sin esta guarda nadie sabría por qué:
   * contraentrega quedaría "habilitada" y jamás disponible. Se rechaza al construir, que es cuando
   * se puede corregir el despliegue.
   */
  @Test
  void unMinimoMayorQueElMaximoNoSePuedeConfigurar() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CriteriosContraentrega(
                    true, Dinero.deCop(2_000_001), Dinero.deCop(2_000_000), Set.of()));

    assertTrue(error.getMessage().contains("no puede superar al máximo"), error.getMessage());
  }
}
