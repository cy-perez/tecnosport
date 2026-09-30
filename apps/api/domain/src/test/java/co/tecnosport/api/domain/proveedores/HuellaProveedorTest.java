package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HuellaProveedorTest {

  private static final UUID PROVEEDOR = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a6");
  private static final UUID OTRO = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a7");

  /** Dos escrituras del mismo anuncio dan la misma huella: de eso depende la renovación. */
  @Test
  void elMismoProductoEscritoDistintoDaLaMismaHuella() {
    HuellaProveedor una =
        HuellaProveedor.calcular(PROVEEDOR, "Bolso de dama mediano 👜", Dinero.deCop(53000));
    HuellaProveedor otra =
        HuellaProveedor.calcular(PROVEEDOR, "  BOLSO DE DAMA MEDIANO ", Dinero.deCop(53000));

    assertEquals(una, otra);
    assertEquals(64, una.valor().length());
  }

  /** «Conjunto pantalón» a 60.000 y a 45.000 son dos productos: el precio va dentro. */
  @Test
  void elMismoTituloAOtroPrecioEsOtraHuella() {
    HuellaProveedor burda =
        HuellaProveedor.calcular(PROVEEDOR, "Conjunto pantalón", Dinero.deCop(60000));
    HuellaProveedor algodon =
        HuellaProveedor.calcular(PROVEEDOR, "Conjunto pantalón", Dinero.deCop(45000));

    assertNotEquals(burda, algodon);
  }

  @Test
  void elMismoAnuncioDeOtroProveedorEsOtraHuella() {
    assertNotEquals(
        HuellaProveedor.calcular(PROVEEDOR, "Morral dúo", Dinero.deCop(60000)),
        HuellaProveedor.calcular(OTRO, "Morral dúo", Dinero.deCop(60000)));
  }

  @Test
  void sinTituloConLetrasNoHayHuella() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> HuellaProveedor.calcular(PROVEEDOR, "🌈🌸", Dinero.deCop(53000)));
    assertThrows(
        ExcepcionDeDominio.class, () -> HuellaProveedor.calcular(PROVEEDOR, "Bolso", null));
    assertThrows(ExcepcionDeDominio.class, () -> new HuellaProveedor("abc"));
  }
}
