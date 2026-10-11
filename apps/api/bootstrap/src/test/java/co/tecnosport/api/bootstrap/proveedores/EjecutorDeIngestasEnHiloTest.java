package co.tecnosport.api.bootstrap.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ColaDeIngestasLlenaException;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** La cola en memoria de local: un tramo a la vez, en orden, y acotada. */
class EjecutorDeIngestasEnHiloTest {

  private EjecutorDeIngestasEnHilo ejecutor;

  @AfterEach
  void apagar() throws InterruptedException {
    if (ejecutor != null) {
      ejecutor.destroy();
    }
  }

  private static LoteIngesta loteDe(UUID id) {
    return LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/" + id + ".zip", Instant.now());
  }

  @Test
  void procesaLosTramosDeUnoEnUnoYEnElOrdenEnQueLlegan() throws Exception {
    List<UUID> procesados = new CopyOnWriteArrayList<>();
    CountDownLatch tres = new CountDownLatch(3);
    ejecutor =
        new EjecutorDeIngestasEnHilo(
            id -> {
              procesados.add(id);
              tres.countDown();
              return loteDe(id);
            },
            5);
    List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    ids.forEach(ejecutor::encolar);

    assertTrue(tres.await(5, TimeUnit.SECONDS));
    assertEquals(ids, procesados);
  }

  /** Lo que no cabe se rechaza al encolar, no se pierde en silencio. */
  @Test
  void unaColaLlenaSeDiceAlEncolar() throws Exception {
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
            1);

    ejecutor.encolar(UUID.randomUUID());
    Thread.sleep(50);
    ejecutor.encolar(UUID.randomUUID());

    assertThrows(ColaDeIngestasLlenaException.class, () -> ejecutor.encolar(UUID.randomUUID()));
    soltar.countDown();
  }

  /** Un tramo que revienta no se lleva el hilo: el siguiente se procesa igual. */
  @Test
  void unTramoQueRevientaNoDetieneLaCola() throws Exception {
    CountDownLatch elSegundo = new CountDownLatch(1);
    UUID malo = UUID.randomUUID();
    ejecutor =
        new EjecutorDeIngestasEnHilo(
            id -> {
              if (id.equals(malo)) {
                throw new IllegalStateException("se cayó");
              }
              elSegundo.countDown();
              return loteDe(id);
            },
            5);

    ejecutor.encolar(malo);
    ejecutor.encolar(UUID.randomUUID());

    assertTrue(elSegundo.await(5, TimeUnit.SECONDS));
  }

  @Test
  void laColaEnMemoriaNoSobreviveAUnReinicio() {
    ejecutor = new EjecutorDeIngestasEnHilo(EjecutorDeIngestasEnHiloTest::loteDe, 1);

    assertFalse(ejecutor.sobreviveAUnReinicio());
  }
}
