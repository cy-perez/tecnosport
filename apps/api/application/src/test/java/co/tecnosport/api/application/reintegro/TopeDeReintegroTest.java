package co.tecnosport.api.application.reintegro;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

  /** Un reintegro en tramos es legitimo mientras la suma quepa. */
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

  /**
   * Doble escrito a mano, sin Mockito (docs/06-testing.md). Anidado y no un archivo mas: ya hay
   * cuatro copias identicas de este doble, una por paquete de prueba, y no hacen falta cinco.
   */
  private static final class RepositorioReintegrosFalso implements RepositorioReintegros {

    private final List<Reintegro> guardados = new ArrayList<>();

    @Override
    public void guardar(Reintegro reintegro) {
      guardados.removeIf(r -> r.id().equals(reintegro.id()));
      guardados.add(reintegro);
    }

    @Override
    public Optional<Reintegro> buscarPorId(UUID id) {
      return guardados.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    @Override
    public List<Reintegro> buscarPorPedido(UUID pedidoId) {
      return guardados.stream().filter(r -> r.pedidoId().equals(pedidoId)).toList();
    }

    @Override
    public Optional<Reintegro> buscarPorOrigen(UUID origenId) {
      return guardados.stream().filter(r -> r.origenId().equals(origenId)).findFirst();
    }
  }
}
