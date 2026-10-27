package co.tecnosport.api.infrastructure.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.MarcapasosDeIngesta;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** El latido acompaña al lote mientras el tramo trabaja, y solo entonces. */
class MarcapasosConTemporizadorTest {

  private static final Duration LATIDO = Duration.ofMillis(20);

  private final List<UUID> latidos = new CopyOnWriteArrayList<>();
  private MarcapasosConTemporizador marcapasos;

  @AfterEach
  void apagar() {
    if (marcapasos != null) {
      marcapasos.destroy();
    }
  }

  @Test
  void lateMientrasEstaAbiertoYDejaDeLatirAlCerrarlo() throws Exception {
    marcapasos = new MarcapasosConTemporizador(latidos::add, LATIDO);
    UUID loteId = UUID.randomUUID();

    MarcapasosDeIngesta.Latido latido = marcapasos.latirPor(loteId);
    Thread.sleep(LATIDO.toMillis() * 6);
    latido.close();

    assertTrue(latidos.size() >= 2, "latió " + latidos.size() + " vez/veces");
    assertTrue(latidos.stream().allMatch(loteId::equals));
    Thread.sleep(LATIDO.toMillis() * 3);
    int alCerrar = latidos.size();
    Thread.sleep(LATIDO.toMillis() * 5);
    assertEquals(alCerrar, latidos.size(), "siguió latiendo con el tramo ya terminado");
  }

  /** Si un latido falla, los siguientes siguen llegando: si no, el lote se daría por abandonado. */
  @Test
  void unLatidoQueFallaNoDetieneLosSiguientes() throws Exception {
    CountDownLatch tresIntentos = new CountDownLatch(3);
    marcapasos =
        new MarcapasosConTemporizador(
            id -> {
              tresIntentos.countDown();
              throw new IllegalStateException("la base no responde");
            },
            LATIDO);

    try (MarcapasosDeIngesta.Latido latido = marcapasos.latirPor(UUID.randomUUID())) {
      assertTrue(tresIntentos.await(5, TimeUnit.SECONDS), "dejó de latir tras el primer fallo");
    }
  }
}
