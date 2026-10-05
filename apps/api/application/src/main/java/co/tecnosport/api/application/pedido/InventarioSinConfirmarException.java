package co.tecnosport.api.application.pedido;

import java.util.UUID;

/**
 * El pedido está pagado pero alguna línea no tiene la unidad: no se puede preparar. Las salidas son
 * reponer y volver a intentarlo, o cancelarlo con reintegro.
 */
public class InventarioSinConfirmarException extends RuntimeException {

  public InventarioSinConfirmarException(UUID pedidoId) {
    super(
        "El pedido "
            + pedidoId
            + " está pagado, pero no hay existencia para todas sus líneas: repón y vuelve a"
            + " intentarlo, o cancélalo con reintegro.");
  }
}
