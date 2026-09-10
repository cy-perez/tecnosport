package co.tecnosport.api.application.retracto;

import java.util.UUID;

/**
 * El plazo de retracto se cuenta desde la entrega (Ley 1480 de 2011, art. 47), así que sobre un
 * pedido que todavía no se entregó no hay nada que radicar: el derecho ni siquiera empezó a correr.
 */
public class PedidoSinEntregarException extends RuntimeException {

  public PedidoSinEntregarException(UUID pedidoId) {
    super("El pedido " + pedidoId + " todavía no se ha entregado.");
  }
}
