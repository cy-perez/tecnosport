package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * @param tallaSirveHasta hasta qué talla sirve una prenda de talla única; nulo es «no lo toques» y
 *     en blanco es «quítalo»
 */
public record EditarProductoComando(
    UUID productoId,
    String nombre,
    String descripcion,
    UUID marcaId,
    UUID categoriaId,
    String tallaSirveHasta) {

  public EditarProductoComando(
      UUID productoId, String nombre, String descripcion, UUID marcaId, UUID categoriaId) {
    this(productoId, nombre, descripcion, marcaId, categoriaId, null);
  }
}
