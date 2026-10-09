package co.tecnosport.api.application.catalogo;

/**
 * Una marca con productos no se borra: la llave de {@code producto.marca_id} no lo permitiría, y
 * aunque lo hiciera, el producto se quedaría sin marca en la ficha y en el filtro. Primero se
 * mueven los productos a otra marca, desde su formulario.
 */
public final class MarcaConProductosException extends RuntimeException {

  public MarcaConProductosException(String nombre, long productos) {
    super(
        "La marca '"
            + nombre
            + "' tiene "
            + productos
            + (productos == 1 ? " producto" : " productos")
            + ". Muévelos a otra marca antes de eliminarla.");
  }

  /** Cuando la cuenta no se sabe: la base rechazó el borrado porque entró un producto a la vez. */
  public MarcaConProductosException(String nombre) {
    super("La marca '" + nombre + "' tiene productos. Muévelos a otra marca antes de eliminarla.");
  }
}
