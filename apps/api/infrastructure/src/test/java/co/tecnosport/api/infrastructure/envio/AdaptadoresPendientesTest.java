package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Las dos piezas del seguimiento que todavía no se pueden escribir, porque la cuenta de sandbox no
 * tiene créditos para emitir una guía y nadie ha visto un evento real
 * (docs/13-skydropx-capacidades.md, sección 6).
 *
 * <p>Eran tres. La firma salió de esta lista el 14 de septiembre de 2026, cuando la documentación
 * oficial resolvió el algoritmo: ver {@link VerificadorFirmaEnvioHmacTest}. Las otras dos siguen
 * dependiendo de ver un evento de verdad, porque lo que falta de ellas es la <em>forma</em> del
 * cuerpo, y esa no la dice ninguna especificación.
 *
 * <p>Parecen demasiado triviales para probarlas, y es justo al revés: son las que alguien va a
 * reemplazar. Sin esto, nada avisa si se quedan a medio implementar.
 */
class AdaptadoresPendientesTest {

  @Test
  void elLectorNoSabeLeerNingunCuerpo() {
    LectorEventoDeEnvioPendiente lector = new LectorEventoDeEnvioPendiente();

    assertTrue(lector.leer("{\"estado\":\"delivered\",\"guia\":\"NN-1\"}").isEmpty());
    assertTrue(lector.leer("no es json").isEmpty());
  }

  /**
   * Lista vacía y no una excepción: para la conciliación eso es "sin novedad", y la tarea sigue.
   */
  @Test
  void elConsultorNoDevuelveEventos() {
    ConsultorDeSeguimientoPendiente consultor = new ConsultorDeSeguimientoPendiente();

    assertTrue(consultor.consultar("99 minutes", "NN-1").isEmpty());
  }
}
