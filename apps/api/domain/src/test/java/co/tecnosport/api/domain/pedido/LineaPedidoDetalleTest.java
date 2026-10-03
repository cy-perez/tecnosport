package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lo que se eligió de la variante, congelado en la línea (3 de octubre de 2026). */
class LineaPedidoDetalleTest {

  private static LineaPedido linea(String detalle) {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("PRV-1"),
        "Bodi herraje",
        1,
        Dinero.deCop(60000),
        BigDecimal.ZERO,
        null,
        UUID.randomUUID(),
        detalle);
  }

  @Test
  void laDescripcionDiceLoQueSeEligio() {
    assertEquals("Bodi herraje (Negro · Única)", linea("Negro · Única").descripcion());
  }

  @Test
  void sinDetalleLaDescripcionEsElNombre() {
    assertEquals("Bodi herraje", linea(null).descripcion());
    assertEquals("Bodi herraje", linea("  ").descripcion());
    assertNull(linea("  ").detalleVariante());
  }
}
