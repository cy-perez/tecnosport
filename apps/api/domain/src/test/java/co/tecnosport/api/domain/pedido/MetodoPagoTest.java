package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Quién cobra cada método ({@code adr/0048}). Antes de que existieran dos pasarelas esto era un
 * booleano y no tenía prueba propia: no hacía falta, porque "pasa por la pasarela" solo podía
 * querer decir Wompi.
 *
 * <p>La última de estas pruebas era, hasta el 22 de septiembre de 2026, "Addi sigue apuntando a
 * Wompi y es lo que lo mantiene fuera del checkout". Ese valor salió del enum (V61) y con él la
 * prueba: lo que queda en su lugar fija qué métodos cobra Wompi, para que sumar uno sea un acto
 * deliberado y no el efecto lateral de otra cosa — que es exactamente lo que le pasó a Addi.
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
   * Los métodos que cobra Wompi son exactamente estos cuatro. La prueba no es decorativa: un valor
   * enrutado a una pasarela que no lo ofrece se cuela en el checkout en cuanto alguien toque la
   * lista de habilitados de esa cuenta, y así estuvo Addi cuatro meses — apuntando a Wompi, donde
   * no existe, y fuera del checkout solo porque la lista no lo incluía.
   */
  @Test
  void losQueCobraWompiSonExactamenteEsosCuatro() {
    Set<MetodoPago> deWompi =
        Arrays.stream(MetodoPago.values())
            .filter(metodo -> metodo.pasarela() == ProveedorDePago.WOMPI)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(MetodoPago.class)));

    assertEquals(
        EnumSet.of(MetodoPago.TARJETA, MetodoPago.PSE, MetodoPago.NEQUI, MetodoPago.BANCOLOMBIA),
        deWompi);
  }
}
