package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Las tres piezas del seguimiento que todavía no se pueden escribir, porque la cuenta de sandbox no
 * tiene créditos para emitir una guía y nadie ha visto un evento real
 * (docs/13-skydropx-capacidades.md, sección 6).
 *
 * <p>Parecen demasiado triviales para probarlas, y es justo al revés: son las tres que alguien va a
 * reemplazar. Sin esto, nada avisa si se quedan a medio implementar — y la del verificador es la
 * peor de las tres, porque un verificador que aceptara de más convierte un endpoint público en la
 * forma de marcar cualquier pedido como entregado.
 */
class AdaptadoresPendientesTest {

  @Test
  void elVerificadorDeFirmaRechazaTodo() {
    VerificadorFirmaEnvioPendiente verificador = new VerificadorFirmaEnvioPendiente();

    assertFalse(verificador.esValida("{\"evento\":1}", "HMAC loquesea"));
    assertFalse(verificador.esValida("{\"evento\":1}", null));
    assertFalse(verificador.esValida("", ""));
  }

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
