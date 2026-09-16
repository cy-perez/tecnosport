package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;

/** Las guías se validan en {@code GuiaEnvio} y {@code Envio}, no aquí. */
public record DespacharPedidoRequest(List<GuiaDespachadaRequest> guias) {

  /** {@code costoEnvio} en pesos enteros, como el resto de la API. */
  public record GuiaDespachadaRequest(String transportadora, String guia, long costoEnvio) {}
}
