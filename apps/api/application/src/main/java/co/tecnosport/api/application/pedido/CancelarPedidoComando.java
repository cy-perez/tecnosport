package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.MotivoCancelacion;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code monto}, {@code medio} y {@code comprobante} solo hacen falta cuando el dinero ya habia
 * entrado. Un contraentrega sin despachar no cobro nada, y un pago pendiente tampoco: exigir ahi
 * una constancia obligaria a inventar un reintegro que nunca ocurrio.
 */
public record CancelarPedidoComando(
    UUID pedidoId,
    MotivoCancelacion motivo,
    BigDecimal monto,
    MedioReintegro medio,
    String comprobante,
    String actor) {

  public CancelarPedidoComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
