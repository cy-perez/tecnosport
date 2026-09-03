package co.tecnosport.api.presentation.pedido.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}: precio, SKU, nombre e imagen los
 * decide el servidor con el catálogo real (docs/00-producto.md). Sin Bean Validation en este
 * proyecto todavía — se valida a mano en el constructor compacto, mismo patrón que {@code
 * AgregarLineaRequest}.
 */
public record CrearPedidoRequest(
    String correo,
    List<LineaRequest> lineas,
    String tipoEntrega,
    DireccionRequest direccion,
    String metodoPago) {

  public CrearPedidoRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (lineas == null || lineas.isEmpty()) {
      throw new IllegalArgumentException("lineas no puede estar vacío.");
    }
    if (tipoEntrega == null || tipoEntrega.isBlank()) {
      throw new IllegalArgumentException("tipoEntrega es obligatorio.");
    }
    if (metodoPago == null || metodoPago.isBlank()) {
      throw new IllegalArgumentException("metodoPago es obligatorio.");
    }
  }

  public record LineaRequest(UUID varianteId, int cantidad) {

    public LineaRequest {
      if (varianteId == null) {
        throw new IllegalArgumentException("varianteId es obligatorio.");
      }
    }
  }

  public record DireccionRequest(
      String codigoDaneDepartamento,
      String departamento,
      String codigoDaneCiudad,
      String ciudad,
      String direccion,
      String indicaciones) {}
}
