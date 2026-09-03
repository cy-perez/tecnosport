package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PoliticaContraentregaTest {

  private static final CriteriosContraentrega CRITERIOS =
      new CriteriosContraentrega(true, Dinero.deCop(1_000_000), Set.of(LineaCatalogo.CELULARES));

  @Test
  void disponibleCuandoCumpleTodasLasReglas() {
    assertTrue(
        PoliticaContraentrega.disponible(
            CRITERIOS, Dinero.deCop(200_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO), true, false));
  }

  @Test
  void noDisponibleSiContraentregaEstaDeshabilitadaGlobalmente() {
    CriteriosContraentrega deshabilitada =
        new CriteriosContraentrega(false, Dinero.deCop(1_000_000), Set.of());
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
}
