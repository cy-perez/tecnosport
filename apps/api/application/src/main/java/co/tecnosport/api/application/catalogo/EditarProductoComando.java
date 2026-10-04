package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * @param tallaSirveHasta hasta qué talla sirve una prenda de talla única; nulo es «no lo toques» y
 *     en blanco es «quítalo»
 * @param fotosGeneralesEnCadaColor si las fotos sin tono acompañan a cada color; nulo es «no lo
 *     toques»
 */
public record EditarProductoComando(
    UUID productoId,
    String nombre,
    String descripcion,
    UUID marcaId,
    UUID categoriaId,
    String tallaSirveHasta,
    Boolean fotosGeneralesEnCadaColor) {

  public EditarProductoComando(
      UUID productoId, String nombre, String descripcion, UUID marcaId, UUID categoriaId) {
    this(productoId, nombre, descripcion, marcaId, categoriaId, null, null);
  }

  public EditarProductoComando(
      UUID productoId,
      String nombre,
      String descripcion,
      UUID marcaId,
      UUID categoriaId,
      String tallaSirveHasta) {
    this(productoId, nombre, descripcion, marcaId, categoriaId, tallaSirveHasta, null);
  }
}
