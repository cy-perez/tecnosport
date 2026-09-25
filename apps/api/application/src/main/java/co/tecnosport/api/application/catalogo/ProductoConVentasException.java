package co.tecnosport.api.application.catalogo;

/**
 * Alguna variante de este producto aparece en un pedido, así que el producto se queda.
 *
 * <p>La línea de pedido guarda su propia copia del SKU, el nombre y el precio —por eso un pedido
 * viejo sigue leyéndose entero aunque el catálogo haya cambiado—, pero no todo lo que cuelga de una
 * venta está congelado: la garantía y el retracto buscan el producto <b>a partir del id de la
 * variante</b> para saber de qué se está reclamando. Borrado el producto, esa búsqueda no encuentra
 * nada y la reclamación se queda sin respuesta, dos años después de la compra, que es justo el
 * plazo en el que la garantía legal sigue viva.
 *
 * <p>La salida no es borrar: es retirar de la vitrina, que es exactamente para lo que existe {@code
 * DespublicarProducto}. Lo que se borra es lo que nunca llegó a venderse — un producto cargado por
 * error, un duplicado, una prueba.
 */
public final class ProductoConVentasException extends RuntimeException {

  public ProductoConVentasException(String nombre) {
    super(
        "El producto '"
            + nombre
            + "' tiene ventas: no se puede borrar. Retíralo de la vitrina en vez de borrarlo.");
  }
}
