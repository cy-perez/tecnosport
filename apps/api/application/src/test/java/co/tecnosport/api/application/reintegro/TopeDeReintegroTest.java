package co.tecnosport.api.application.reintegro;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RepositorioReintegrosFalso;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El tope de lo que un pedido puede devolver, que es la regla que los cuatro caminos aplicaban cada
 * uno a su manera y ninguno completo.
 */
class TopeDeReintegroTest {

  private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
  private static final Dinero TOTAL = Dinero.deCop(BigDecimal.valueOf(50_000));

  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();
  private final TopeDeReintegro tope = new TopeDeReintegro(reintegros);
  private final UUID pedidoId = UUID.randomUUID();

  /** Devolver el total completo es lo normal en un retracto, y es lo que precarga el panel. */
  @Test
  void elTotalCompletoCabe() {
    assertDoesNotThrow(() -> tope.exigirQueQuepa(pedidoId, TOTAL, TOTAL));
  }

  @Test
  void unSoloMontoPorEncimaDelTotalNoCabe() {
    MontoDeReintegroInvalidoException error =
        assertThrows(
            MontoDeReintegroInvalidoException.class,
            () -> tope.exigirQueQuepa(pedidoId, TOTAL, Dinero.deCop(BigDecimal.valueOf(50_001))));

    // Sin nada devuelto antes, el mensaje no habla de lo ya devuelto: seria ruido.
    assertTrue(error.getMessage().contains("supera el total del pedido"), error.getMessage());
  }

  /**
   * <b>La prueba del hallazgo.</b> Antes del tope, esto pasaba: cada camino comparaba su propio
   * monto contra el total y nadie sumaba, asi que un pedido de 50.000 admitia un reintegro de
   * 50.000 por retracto y otro de 50.000 por garantia — cien mil devueltos sobre una venta de
   * cincuenta.
   */
  @Test
  void loQueYaSeDevolvioConsumeElTope() {
    devolver(MotivoReintegro.RETRACTO, 50_000);

    MontoDeReintegroInvalidoException error =
        assertThrows(
            MontoDeReintegroInvalidoException.class,
            () -> tope.exigirQueQuepa(pedidoId, TOTAL, TOTAL));

    // El mensaje dice la cifra ya devuelta: sin ella, rechazar 50.000 en un pedido de 50.000 parece
    // un error del sistema.
    assertTrue(error.getMessage().contains("ya se devolvieron 50000"), error.getMessage());
  }

  /**
   * Dos constancias del mismo pedido caben mientras la suma quepa, y eso es lo que ocurre cuando el
   * dinero sale por dos caminos distintos —una garantia y despues una cancelacion, cada una con su
   * propio origen—. Lo que <b>no</b> es posible es partir el reintegro de <i>una misma</i>
   * solicitud en dos tramos: el indice ux_reintegro_origen (V23) es unico por origen y la maquina
   * de estados de la solicitud lo mata antes. De ahi que aqui cada tramo lleve un origen distinto:
   * probar lo contrario seria sugerir una capacidad que el esquema prohibe.
   */
  @Test
  void variosTramosCabenSiSumanElTotal() {
    devolver(MotivoReintegro.GARANTIA, 30_000);

    assertDoesNotThrow(
        () -> tope.exigirQueQuepa(pedidoId, TOTAL, Dinero.deCop(BigDecimal.valueOf(20_000))));
    assertThrows(
        MontoDeReintegroInvalidoException.class,
        () -> tope.exigirQueQuepa(pedidoId, TOTAL, Dinero.deCop(BigDecimal.valueOf(20_001))));
  }

  /** La plata no sabe de motivos: el tope es del pedido, no de cada figura legal. */
  @Test
  void sumaLosMotivosDistintos() {
    devolver(MotivoReintegro.RETRACTO, 10_000);
    devolver(MotivoReintegro.GARANTIA, 15_000);
    devolver(MotivoReintegro.NO_DISPONIBILIDAD, 5_000);

    assertEquals(Dinero.deCop(BigDecimal.valueOf(30_000)), tope.yaDevuelto(pedidoId));
  }

  /** Lo devuelto por otro pedido no consume este tope. */
  @Test
  void loDevueltoPorOtroPedidoNoCuenta() {
    reintegros.guardar(
        Reintegro.registrar(
            UUID.randomUUID(),
            MotivoReintegro.RETRACTO,
            UUID.randomUUID(),
            TOTAL,
            MedioReintegro.TRANSFERENCIA_BANCARIA,
            null,
            AHORA,
            "admin:1"));

    assertEquals(Dinero.deCop(BigDecimal.ZERO), tope.yaDevuelto(pedidoId));
    assertDoesNotThrow(() -> tope.exigirQueQuepa(pedidoId, TOTAL, TOTAL));
  }

  private void devolver(MotivoReintegro motivo, long monto) {
    reintegros.guardar(
        Reintegro.registrar(
            pedidoId,
            motivo,
            UUID.randomUUID(),
            Dinero.deCop(BigDecimal.valueOf(monto)),
            MedioReintegro.TRANSFERENCIA_BANCARIA,
            null,
            AHORA,
            "admin:1"));
  }
}
