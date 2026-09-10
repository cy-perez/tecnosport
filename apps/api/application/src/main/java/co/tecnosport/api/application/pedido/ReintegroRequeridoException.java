package co.tecnosport.api.application.pedido;

import java.util.UUID;

/**
 * Cancelar un pedido cuyo dinero ya entró exige devolverlo, y devolverlo exige dejar constancia. Un
 * pedido cancelado sin reintegro cuando el comprador ya había pagado es plata retenida sin
 * explicación — y es la clase de error que un panel deja pasar en silencio si nadie lo bloquea.
 */
public class ReintegroRequeridoException extends RuntimeException {

  public ReintegroRequeridoException(UUID pedidoId) {
    super(
        "El pedido "
            + pedidoId
            + " ya tenía el dinero recibido: cancelarlo exige el monto y el medio del reintegro.");
  }
}
