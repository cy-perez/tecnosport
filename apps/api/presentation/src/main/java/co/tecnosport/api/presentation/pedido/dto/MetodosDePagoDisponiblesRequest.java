package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;

public record MetodosDePagoDisponiblesRequest(
    List<CrearPedidoRequest.LineaRequest> lineas,
    String correo,
    String tipoEntrega,
    CrearPedidoRequest.DireccionRequest direccion) {

  public MetodosDePagoDisponiblesRequest {
    if (lineas == null || lineas.isEmpty()) {
      throw new IllegalArgumentException("lineas no puede estar vacío.");
    }
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (tipoEntrega == null || tipoEntrega.isBlank()) {
      throw new IllegalArgumentException("tipoEntrega es obligatorio.");
    }
  }
}
