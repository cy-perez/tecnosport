package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * El pedido no se entrega por ese camino: una recogida no se despacha con transportadora, y un
 * envío a domicilio no se recoge en el punto (ADR-0094). La lanza {@link Pedido}: el camino de
 * entrega es un invariante del agregado, no de cada caso de uso que lo mueve.
 */
public final class EntregaNoAplicaException extends ExcepcionDeDominio {

  public EntregaNoAplicaException(UUID pedidoId, String motivo) {
    super("El pedido " + pedidoId + " no se puede entregar así: " + motivo + ".");
  }
}
