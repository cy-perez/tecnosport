package co.tecnosport.api.application.pedido;

import java.util.UUID;

/**
 * El pedido no se entrega por ese camino: una recogida no se despacha con transportadora, y un
 * envío a domicilio no se recoge en el punto (ADR-0094).
 */
public final class EntregaNoAplicaException extends RuntimeException {

  public EntregaNoAplicaException(UUID pedidoId, String motivo) {
    super("El pedido " + pedidoId + " no se puede entregar así: " + motivo + ".");
  }
}
