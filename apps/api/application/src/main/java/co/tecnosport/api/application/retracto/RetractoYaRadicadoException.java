package co.tecnosport.api.application.retracto;

import java.util.UUID;

/** Ya hay una solicitud de retracto en curso para ese pedido. */
public class RetractoYaRadicadoException extends RuntimeException {

  public RetractoYaRadicadoException(UUID pedidoId) {
    super("El pedido " + pedidoId + " ya tiene una solicitud de retracto en curso.");
  }
}
