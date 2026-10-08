package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.pedido.Direccion;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}, igual que {@code
 * CrearPedidoComando} y {@code MetodosDePagoDisponiblesComando}: el peso, las dimensiones y el
 * valor de cada variante los resuelve el caso de uso contra el catálogo real. El cliente no cotiza
 * con los pesos que él diga (regla dura #7).
 *
 * <p>{@code transportadora} es la que eligió el comprador (ADR-0073), por su nombre, o nulo para la
 * más económica. Es lo único de la tarifa que viaja desde el cliente: el costo nunca.
 */
public record CotizarEnvioComando(
    List<LineaComando> lineas, Direccion direccion, boolean conRecaudo, String transportadora) {

  /** Sin recaudo: lo que pide el resumen del checkout, antes de elegir método de pago. */
  public CotizarEnvioComando(List<LineaComando> lineas, Direccion direccion) {
    this(lineas, direccion, false, null);
  }

  /** Sin transportadora elegida: la más económica, como antes de ADR-0073. */
  public CotizarEnvioComando(List<LineaComando> lineas, Direccion direccion, boolean conRecaudo) {
    this(lineas, direccion, conRecaudo, null);
  }

  public record LineaComando(UUID varianteId, int cantidad) {}
}
