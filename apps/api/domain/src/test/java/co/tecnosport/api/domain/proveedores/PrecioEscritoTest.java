package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import org.junit.jupiter.api.Test;

class PrecioEscritoTest {

  /** El 135.000 de Tenis Al Pie va escrito «💰135» detrás del rótulo: está escrito. */
  @Test
  void losMilesDetrasDelRotuloPorMayorCuentanComoEscritos() {
    String texto = "Detal :$180.000 \nX mayor unidad 💰135";

    assertTrue(PrecioEscrito.en(Dinero.deCop(135000), texto));
    assertTrue(PrecioEscrito.en(Dinero.deCop(180000), texto));
  }

  /** Sin el rótulo, «135» suelto no escribe ningún precio. */
  @Test
  void sinElRotuloTresCifrasNoEscribenMiles() {
    assertFalse(PrecioEscrito.en(Dinero.deCop(135000), "Referencia 135, tallas 36 a 39"));
  }
}
