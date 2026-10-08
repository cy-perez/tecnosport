package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;

public record MetodosDePagoDisponiblesRequest(
    List<CrearPedidoRequest.LineaRequest> lineas,
    String correo,
    String tipoEntrega,
    CrearPedidoRequest.DireccionRequest direccion,
    /** La transportadora elegida: la contraentrega solo se ofrece si esa recauda (ADR-0073). */
    String transportadora) {

  /** Sin transportadora elegida. */
  public MetodosDePagoDisponiblesRequest(
      List<CrearPedidoRequest.LineaRequest> lineas,
      String correo,
      String tipoEntrega,
      CrearPedidoRequest.DireccionRequest direccion) {
    this(lineas, correo, tipoEntrega, direccion, null);
  }

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
