package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * La pieza del seguimiento que todavía no se puede escribir.
 *
 * <p>Eran tres. La firma salió de esta lista el 14 de septiembre de 2026, cuando la documentación
 * oficial resolvió el algoritmo (ver {@link VerificadorFirmaEnvioHmacTest}), y el consultor de
 * rastreo el 16, midiendo con {@code tools/sonda-rastreo.mjs} sobre una guía emitida (ver {@link
 * MapeadorSeguimientoSkydropxV1Test}). Queda el lector del cuerpo del webhook.
 *
 * <p>Parece demasiado trivial para probarlo, y es justo al revés: es el que alguien va a
 * reemplazar. Sin esto, nada avisa si se queda a medio implementar.
 */
class AdaptadoresPendientesTest {

  @Test
  void elLectorNoSabeLeerNingunCuerpo() {
    LectorEventoDeEnvioPendiente lector = new LectorEventoDeEnvioPendiente();

    assertTrue(lector.leer("{\"estado\":\"delivered\",\"guia\":\"NN-1\"}").isEmpty());
    assertTrue(lector.leer("no es json").isEmpty());
  }
}
