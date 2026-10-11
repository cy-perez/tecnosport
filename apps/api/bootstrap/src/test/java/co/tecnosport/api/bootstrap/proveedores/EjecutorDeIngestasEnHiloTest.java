package co.tecnosport.api.bootstrap.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** El latido acompaña al lote mientras se procesa, y solo entonces. */
class EjecutorDeIngestasEnHiloTest {

  private static final Duration LATIDO = Duration.ofMillis(20);

  private final List<UUID> latidos = new CopyOnWriteArrayList<>();
  private EjecutorDeIngestasEnHilo ejecutor;

  @AfterEach
  void apagar() throws InterruptedException {
    if (ejecutor != null) {
      ejecutor.destroy();
    }
  }

  /** Lo que devuelve el trabajo; el ejecutor solo lo usa para el registro. */
  private static LoteIngesta loteDe(UUID id) {
    return LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/" + id + ".zip", Instant.now());
  }

  @Test
  void lateMientrasProcesaYDejaDeLatirAlSoltarlo() throws Exception {
    UUID loteId = UUID.randomUUID();
    CountDownLatch empezo = new CountDownLatch(1);
    CountDownLatch soltar = new CountDownLatch(1);
    CountDownLatch termino = new CountDownLatch(1);
    Function<UUID, LoteIngesta> procesar =
        id -> {
          empezo.countDown();
          try {
            soltar.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          termino.countDown();
          return loteDe(id);
        };
    ejecutor = new EjecutorDeIngestasEnHilo(procesar, 5, latidos::add, LATIDO);

    ejecutor.encolar(loteId);
    assertTrue(empezo.await(5, TimeUnit.SECONDS));
    Thread.sleep(LATIDO.toMillis() * 6);
    soltar.countDown();
    assertTrue(termino.await(5, TimeUnit.SECONDS));

    assertTrue(latidos.size() >= 2, "latió " + latidos.size() + " vez/veces");
    assertTrue(latidos.stream().allMatch(loteId::equals));
    Thread.sleep(LATIDO.toMillis() * 3);
    int alSoltar = latidos.size();
    Thread.sleep(LATIDO.toMillis() * 5);
    assertEquals(alSoltar, latidos.size(), "siguió latiendo con el lote ya suelto");
  }

  /** Si un latido falla, los siguientes siguen llegando: si no, el lote se daría por abandonado. */
  @Test
  void unLatidoQueFallaNoDetieneLosSiguientes() throws Exception {
    CountDownLatch tresIntentos = new CountDownLatch(3);
    CountDownLatch soltar = new CountDownLatch(1);
    ejecutor =
        new EjecutorDeIngestasEnHilo(
            id -> {
              try {
                soltar.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return loteDe(id);
            },
            5,
            id -> {
              tresIntentos.countDown();
              throw new IllegalStateException("la base no responde");
            },
            LATIDO);

    ejecutor.encolar(UUID.randomUUID());

    assertTrue(tresIntentos.await(5, TimeUnit.SECONDS), "dejó de latir tras el primer fallo");
    soltar.countDown();
  }
}
