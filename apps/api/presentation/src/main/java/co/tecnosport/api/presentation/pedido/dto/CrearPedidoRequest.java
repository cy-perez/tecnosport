package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.domain.pedido.MetodoPago;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}: precio, SKU, nombre e imagen los
 * decide el servidor con el catálogo real (docs/00-producto.md). Sin Bean Validation en este
 * proyecto todavía — se valida a mano en el constructor compacto, mismo patrón que {@code
 * AgregarLineaRequest}.
 *
 * <p>{@code autorizaDatos} no lleva validación en el constructor compacto: la regla no es de
 * formato sino de negocio, y vive en el dominio (Ley 1581 de 2012). Ojo, Jackson 3 rechaza el
 * cuerpo entero si el campo falta, no lo deja en {@code false} — ver apps/api/CLAUDE.md.
 */
public record CrearPedidoRequest(
    String correo,
    /**
     * Quien recibe: va en la guía y es a quien llama el mensajero. El formato lo valida el dominio.
     */
    String nombre,
    String telefono,
    List<LineaRequest> lineas,
    String tipoEntrega,
    DireccionRequest direccion,
    MetodoPago metodoPago,
    boolean autorizaDatos) {

  public CrearPedidoRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (nombre == null || nombre.isBlank()) {
      throw new IllegalArgumentException("nombre es obligatorio.");
    }
    if (telefono == null || telefono.isBlank()) {
      throw new IllegalArgumentException("telefono es obligatorio.");
    }
    if (lineas == null || lineas.isEmpty()) {
      throw new IllegalArgumentException("lineas no puede estar vacío.");
    }
    if (tipoEntrega == null || tipoEntrega.isBlank()) {
      throw new IllegalArgumentException("tipoEntrega es obligatorio.");
    }
    // Tipado, no `String`: el OpenAPI publica el enum y el cliente generado lo restringe
    // (docs/09, deuda 25). La guarda sigue haciendo falta y cambia de forma: un valor que el enum
    // no tiene lo rechaza Jackson antes de llegar aqui, pero un componente de tipo referencia que
    // FALTE llega en nulo sin reventar nada (apps/api/CLAUDE.md). Los dos casos salen igual, 422
    // HTTP_MESSAGE_NOT_READABLE, que es como ya responde cualquier otro enum de esta API.
    if (metodoPago == null) {
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

  /**
   * {@code barrio} e {@code indicaciones} son opcionales y el servidor no los exige. El barrio es
   * el {@code area_level3} de la plataforma de envíos: mejora la entrega y no condiciona el precio,
   * que sale del código DANE.
   */
  public record DireccionRequest(
      String codigoDaneDepartamento,
      String departamento,
      String codigoDaneCiudad,
      String ciudad,
      String direccion,
      String indicaciones,
      String barrio) {}
}
