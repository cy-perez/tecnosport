package co.tecnosport.api.presentation.envio.dto;

import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import java.util.List;

/**
 * Las mismas líneas que {@code CrearPedidoRequest} y {@code MetodosDePagoDisponiblesRequest}: el
 * checkout manda variante y cantidad, y el servidor resuelve peso, dimensiones y valor contra el
 * catálogo. Sin Bean Validation en este proyecto todavía — se valida a mano en el constructor
 * compacto, mismo patrón que los otros dos.
 *
 * <p>Va por {@code POST} y no por {@code GET} aunque no cree nada: el cuerpo lleva la dirección de
 * entrega, y una dirección no va en una URL que queda escrita en los registros del balanceador.
 */
public record CotizacionEnvioRequest(
    List<CrearPedidoRequest.LineaRequest> lineas, CrearPedidoRequest.DireccionRequest direccion) {

  public CotizacionEnvioRequest {
    if (lineas == null || lineas.isEmpty()) {
      throw new IllegalArgumentException("lineas no puede estar vacío.");
    }
    if (direccion == null) {
      throw new IllegalArgumentException("direccion es obligatoria.");
    }
  }
}
