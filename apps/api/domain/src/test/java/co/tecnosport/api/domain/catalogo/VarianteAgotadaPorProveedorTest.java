package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * La prenda de un álbum que el proveedor agotó (13 de octubre de 2026, ADR-0084): sigue activa, y
 * no se vende hasta que la vuelva a anunciar.
 */
class VarianteAgotadaPorProveedorTest {

  private static Variante variante(EstadoVariante estado, boolean agotada) {
    return new Variante(
        UUID.randomUUID(),
        new Sku("PRV-1-NEGRO"),
        Dinero.deCop(115000),
        BigDecimal.ZERO,
        null,
        null,
        estado,
        List.of(),
        null,
        agotada);
  }

  @Test
  void laActivaSinAgotarSeVende() {
    assertTrue(variante(EstadoVariante.ACTIVA, false).seVende());
  }

  @Test
  void laAgotadaPorElProveedorNoSeVendeAunqueEsteActiva() {
    Variante agotada = variante(EstadoVariante.ACTIVA, true);

    assertFalse(agotada.seVende());
    assertTrue(agotada.agotadaPorProveedor());
  }

  @Test
  void laInactivaNoSeVende() {
    assertFalse(variante(EstadoVariante.INACTIVA, false).seVende());
  }

  /** Medirla o cambiarle el precio no la devuelve a la venta. */
  @Test
  void lasCopiasConservanElAgotado() {
    Variante agotada = variante(EstadoVariante.ACTIVA, true);

    assertFalse(agotada.conPrecios(Dinero.deCop(115000), Dinero.deCop(119000)).seVende());
    assertFalse(agotada.medida(new Paquete(300, 30, 25, 5)).seVende());
  }
}
