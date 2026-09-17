package co.tecnosport.api.application.envio;

import java.util.UUID;

/**
 * Ya hay una emisión abierta para este pedido, así que la segunda no sale.
 *
 * <p>Es la puerta que más importa de las tres, porque la plataforma <strong>cobra al crear</strong>
 * y no al entregar la guía: dos solicitudes son dos cobros por el mismo pedido, y el segundo juego
 * de guías no lo va a usar nadie. La idempotencia por {@code rate_id} de Skydropx tampoco cubre
 * esto — cada intento recotiza y trae una tarifa nueva.
 */
public class EmisionYaEnCursoException extends RuntimeException {

  private final UUID pedidoId;
  private final UUID emisionId;

  public EmisionYaEnCursoException(UUID pedidoId, UUID emisionId) {
    super(
        "El pedido "
            + pedidoId
            + " ya tiene la emisión "
            + emisionId
            + " en curso; hay que esperar a que la plataforma responda.");
    this.pedidoId = pedidoId;
    this.emisionId = emisionId;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public UUID emisionId() {
    return emisionId;
  }
}
