package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.pedido.Direccion;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}, igual que {@code
 * CrearPedidoComando} y {@code MetodosDePagoDisponiblesComando}: el peso, las dimensiones y el
 * valor de cada variante los resuelve el caso de uso contra el catálogo real. El cliente no cotiza
 * con los pesos que él diga (regla dura #7).
 */
public record CotizarEnvioComando(
    List<LineaComando> lineas, Direccion direccion, boolean conRecaudo) {

  /** Sin recaudo: lo que pide el resumen del checkout, antes de elegir método de pago. */
  public CotizarEnvioComando(List<LineaComando> lineas, Direccion direccion) {
    this(lineas, direccion, false);
  }

  public record LineaComando(UUID varianteId, int cantidad) {}
}
