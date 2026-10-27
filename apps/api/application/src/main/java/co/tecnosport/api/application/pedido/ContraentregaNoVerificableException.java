package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import java.util.UUID;

/**
 * Solo se verifica un pedido que se cobra al recibir y que sigue esperando la verificación ({@code
 * CONFIRMADO_CONTRAENTREGA}). Uno {@code PAGADO} tiene su propio camino a preparación —{@code
 * ConfirmarInventarioDePedidoPagado}, que confirma la reserva antes— y verificarlo lo saltaba.
 */
public final class ContraentregaNoVerificableException extends RuntimeException {

  public ContraentregaNoVerificableException(
      UUID pedidoId, EstadoPedido estado, MetodoPago metodo) {
    super(
        "El pedido "
            + pedidoId
            + " está en "
            + estado
            + " con método "
            + metodo
            + ": solo se verifica un pedido que se paga al recibir y espera la verificación.");
  }
}
