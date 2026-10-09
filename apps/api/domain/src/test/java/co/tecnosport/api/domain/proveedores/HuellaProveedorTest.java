package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
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

  /**
   * El «Busito manga larga» de La Riverah a 58.000, azul a las 12:06 y gris a las 19:32 del 8 de
   * octubre de 2026: el mismo texto a otra hora es otra prenda, y tiene que poder aprobarse.
   */
  @Test
  void elMismoTextoAOtraHoraEsOtroAnuncio() {
    Instant manana = Instant.parse("2026-10-08T17:06:00Z");
    Instant noche = Instant.parse("2026-10-09T00:32:00Z");

    HuellaProveedor azul =
        HuellaProveedor.deAnuncio(PROVEEDOR, "Busito manga larga", Dinero.deCop(58000), manana);
    HuellaProveedor gris =
        HuellaProveedor.deAnuncio(PROVEEDOR, "Busito manga larga", Dinero.deCop(58000), noche);

    assertNotEquals(azul, gris);
    assertNotEquals(
        HuellaProveedor.calcular(PROVEEDOR, "Busito manga larga", Dinero.deCop(58000)), azul);
    assertEquals(
        azul,
        HuellaProveedor.deAnuncio(PROVEEDOR, " BUSITO MANGA LARGA ", Dinero.deCop(58000), manana),
        "el mismo mensaje leído otra vez es el mismo anuncio");
    assertThrows(
        ExcepcionDeDominio.class,
        () -> HuellaProveedor.deAnuncio(PROVEEDOR, "Busito", Dinero.deCop(58000), null));
  }

  /** El código de Violeta identifica la prenda a cualquier precio, y es de su proveedor. */
  @Test
  void laReferenciaIdentificaSinTituloNiPrecio() {
    assertEquals(
        HuellaProveedor.deReferencia(PROVEEDOR, "Q339"),
        HuellaProveedor.deReferencia(PROVEEDOR, " Q339 "));
    assertNotEquals(
        HuellaProveedor.deReferencia(PROVEEDOR, "Q339"),
        HuellaProveedor.deReferencia(PROVEEDOR, "Q328"));
    assertNotEquals(
        HuellaProveedor.deReferencia(PROVEEDOR, "Q339"),
        HuellaProveedor.deReferencia(OTRO, "Q339"));
    assertThrows(ExcepcionDeDominio.class, () -> HuellaProveedor.deReferencia(PROVEEDOR, " "));
  }
}
