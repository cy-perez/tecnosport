package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * Lo único que el borrado de un lote necesita del catálogo: borrar un producto si se puede. Un
 * puerto y no {@code EliminarProducto} directo para que el caso de uso de proveedores no dependa de
 * cómo se borra un producto —bucket, variantes, inventario—, que es asunto del catálogo.
 */
public interface EliminacionDeProductos {

  /**
   * @return {@code false} si el producto se queda porque está publicado o ya tiene ventas; {@code
   *     true} si se borró o ya no existía
   */
  boolean eliminarSiSePuede(UUID productoId);
}
