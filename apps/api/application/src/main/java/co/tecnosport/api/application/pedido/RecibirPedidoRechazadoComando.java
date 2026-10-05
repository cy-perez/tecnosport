package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.util.Objects;
import java.util.UUID;

/**
 * Sin monto, a propósito: lo que se devuelve lo calcula el caso de uso —lo que el pedido cobró
 * menos lo que ya se devolvió por otros caminos— y no lo teclea nadie (regla dura #7). {@code
 * medio} solo hace falta cuando el dinero había entrado.
 */
public record RecibirPedidoRechazadoComando(
    UUID pedidoId, MedioReintegro medio, String comprobante, String actor) {

  public RecibirPedidoRechazadoComando {
    Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
