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
 *
 * <p><b>Y desde el 28 de septiembre de 2026 ese conjunto tiene un solo elemento</b> ({@code V66}):
 * los cuatro medios de pasarela se agruparon en {@code WOMPI} porque el Web Checkout hospedado
 * nunca recibió cuál había elegido el comprador. La prueba del conjunto no sobra por eso —al revés:
 * ahora lo que vigila es que <b>nadie vuelva a meter un medio suelto</b> al lado de {@code WOMPI},
 * que es justo la forma que tenía el problema.
 */
class MetodoPagoTest {

  @Test
  void loQueCobraWompiLoCobraWompi() {
    assertEquals(ProveedorDePago.WOMPI, MetodoPago.WOMPI.pasarela());
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
   * Lo que cobra Wompi es <b>exactamente un valor</b>, y la prueba no es decorativa: un medio
   * suelto enrutado a una pasarela se cuela en el checkout en cuanto alguien toque la lista de
   * habilitados de esa cuenta, y así estuvo Addi cuatro meses —apuntando a Wompi, donde no existe,
   * y fuera del checkout solo porque la lista no lo incluía—.
   *
   * <p>Con el agrupamiento de la {@code V66} vigila además lo contrario: que no vuelva a aparecer
   * {@code TARJETA}, {@code PSE} o {@code BANCOLOMBIA} al lado de {@code WOMPI}. Dos valores para
   * el mismo cobro son dos formas de contar lo mismo, y una de las dos siempre miente — el checkout
   * no puede prometer cuál de ellos usará el comprador, porque eso se decide en la pantalla de
   * Wompi.
   */
  @Test
  void loQueCobraWompiEsExactamenteUnMetodo() {
    Set<MetodoPago> deWompi =
        Arrays.stream(MetodoPago.values())
            .filter(metodo -> metodo.pasarela() == ProveedorDePago.WOMPI)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(MetodoPago.class)));

    assertEquals(EnumSet.of(MetodoPago.WOMPI), deWompi);
  }
}
