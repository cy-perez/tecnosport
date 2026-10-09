package co.tecnosport.api.bootstrap.proveedores;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.IngestaInterrumpidaException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EsperaDeIngestaConPausaTest {

  @AfterEach
  void limpiarLaMarca() {
    Thread.interrupted();
  }

  /**
   * Al apagar, el ejecutor interrumpe el hilo: la espera sale en vez de volver a dormir, y deja la
   * marca puesta para que nada más en ese hilo se duerma.
   */
  @Test
  void unaInterrupcionSaleConSuExcepcionYConservaLaMarca() {
    EsperaDeIngestaConPausa espera = new EsperaDeIngestaConPausa(Duration.ofMinutes(5));
    Thread.currentThread().interrupt();

    assertThrows(IngestaInterrumpidaException.class, espera::esperar);
    assertTrue(Thread.currentThread().isInterrupted());
  }
}
